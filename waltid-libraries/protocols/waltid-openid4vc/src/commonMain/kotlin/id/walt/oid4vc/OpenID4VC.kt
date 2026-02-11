@file:OptIn(ExperimentalTime::class)

package id.walt.oid4vc

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto.utils.UuidUtils.randomUUIDString
import id.walt.did.dids.DidService
import id.walt.did.dids.DidUtils
import id.walt.mdoc.dataelement.MapElement
import id.walt.oid4vc.data.OpenIDProviderMetadata
import id.walt.oid4vc.data.ResponseMode
import id.walt.oid4vc.data.ResponseType
import id.walt.oid4vc.data.ResponseType.Companion.getResponseTypeString
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.definitions.JWTClaims
import id.walt.oid4vc.errors.AuthorizationError
import id.walt.oid4vc.errors.TokenVerificationError
import id.walt.oid4vc.providers.TokenTarget
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.TokenRequest
import id.walt.oid4vc.responses.AuthorizationCodeResponse
import id.walt.oid4vc.responses.AuthorizationCodeWithAuthorizationRequestResponse
import id.walt.oid4vc.responses.AuthorizationErrorCode
import id.walt.oid4vc.responses.TokenResponse
import id.walt.oid4vc.util.COSESign1Utils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.utils.io.core.*
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

object OpenID4VC {
    private val log = KotlinLogging.logger { }

    suspend fun generateToken(
        sub: String,
        issuer: String,
        audience: TokenTarget,
        tokenId: String? = null,
        tokenKey: Key
    ): String {
        return signToken(
            target = audience,
            payload = buildJsonObject {
                put(JWTClaims.Payload.subject, sub)
                put(JWTClaims.Payload.issuer, issuer)
                put(JWTClaims.Payload.audience, audience.name)
                tokenId?.let { put(JWTClaims.Payload.jwtID, it) }
            },
            privKey = tokenKey
        )
    }

    suspend fun verifyAndParseToken(
        token: String,
        issuer: String,
        target: TokenTarget,
        tokenKey: Key? = null
    ): JsonObject {

        if (!verifyTokenSignature(
                target = target,
                token = token,
                tokenKey = tokenKey
            )
        ) throw IllegalStateException("Invalid token")

        val payload = parseTokenPayload(token)

        val requiredClaims = setOf(
            JWTClaims.Payload.subject,
            JWTClaims.Payload.issuer
        )

        val isValid = requiredClaims.all { it in payload } &&
                payload[JWTClaims.Payload.issuer]?.jsonPrimitive?.content == issuer

        return if (isValid) payload else throw IllegalStateException("Invalid token")
    }

    suspend fun verifyAndParseIdToken(
        token: String,
        tokenKey: Key? = null
    ): JsonObject {
        // 1. Validate Header
        val header = parseTokenHeader(token)
        if (!header.keys.containsAll(
                setOf(
                    JWTClaims.Header.type,
                    JWTClaims.Header.keyID,
                    JWTClaims.Header.algorithm,
                )
            )
        ) {
            throw IllegalStateException("Invalid header in token")
        }

        // 2. Validate Payload
        val payload = parseTokenPayload(token)
        if (!payload.keys.containsAll(
                setOf(
                    JWTClaims.Payload.issuer,
                    JWTClaims.Payload.subject,
                    JWTClaims.Payload.audience,
                    JWTClaims.Payload.expirationTime,
                    JWTClaims.Payload.issuedAtTime,
                    JWTClaims.Payload.nonce,
                )
            )
        ) {
            throw IllegalArgumentException("Invalid payload in token")
        }

        // 3. Verify iss = sub = did
        val sub = payload[JWTClaims.Payload.subject]!!.jsonPrimitive.content
        val iss = payload[JWTClaims.Payload.issuer]!!.jsonPrimitive.content
        val kid = header[JWTClaims.Header.keyID]!!.jsonPrimitive.content
        val did = kid.substringBefore("#")

        if (iss != sub || iss != did || sub != did) {
            log.debug { "$sub $iss $did" }
            throw IllegalArgumentException("Invalid payload in token. sub != iss != did")
        }

        // 4. Verify Signature
        if (!verifyTokenSignature(TokenTarget.TOKEN, token, tokenKey))
            throw IllegalArgumentException("Invalid token - cannot verify signature")

        return payload
    }


    suspend fun generateAuthorizationCodeFor(
        sessionId: String,
        issuer: String,
        tokenKey: Key
    ): String {
        return generateToken(
            sub = sessionId,
            issuer = issuer,
            audience = TokenTarget.TOKEN,
            tokenId = null,
            tokenKey = tokenKey
        )
    }

    suspend fun validateAndParseTokenRequest(
        tokenRequest: TokenRequest,
        issuer: String,
        tokenKey: Key? = null
    ): JsonObject {
        val code = when (tokenRequest) {
            is TokenRequest.AuthorizationCode -> tokenRequest.code
            is TokenRequest.PreAuthorizedCode -> tokenRequest.preAuthorizedCode
        }

        return verifyAndParseToken(
            token = code,
            issuer = issuer,
            target = TokenTarget.TOKEN,
            tokenKey = tokenKey
        )
    }

    // Create an ID or VP Token request using JAR OAuth2.0 specification https://www.rfc-editor.org/rfc/rfc9101.html
    suspend fun processCodeFlowAuthorizationWithAuthorizationRequest(
        authorizationRequest: AuthorizationRequest,
        authServerState: String,
        responseType: ResponseType,
        providerMetadata: OpenIDProviderMetadata,
        tokenKey: Key,
        isJar: Boolean? = true,
        presentationDefinition: PresentationDefinition? = null,
    ): AuthorizationCodeWithAuthorizationRequestResponse {

        providerMetadata.draft11
            ?: providerMetadata.draft13
            ?: error("Unknown metadata type: $providerMetadata")

        if (!authorizationRequest.responseType.contains(ResponseType.Code))
            throw AuthorizationError(
                authorizationRequest = authorizationRequest,
                errorCode = AuthorizationErrorCode.invalid_request,
                message = "Invalid response type ${authorizationRequest.responseType}, for authorization code flow."
            )

        val authorizationRequestServerNonce = randomUUIDString()
        val authorizationResponseServerMode = ResponseMode.direct_post

        val clientId = providerMetadata.issuer!!
        val redirectUri = providerMetadata.issuer + "/direct_post"
        val scope = setOf("openid")

        return AuthorizationCodeWithAuthorizationRequestResponse.success(
            state = authServerState,
            clientId = clientId,
            redirectUri = redirectUri,
            responseType = getResponseTypeString(responseType),
            responseMode = authorizationResponseServerMode,
            scope = scope,
            nonce = authorizationRequestServerNonce,
            requestUri = null,
            request = when (isJar) {
                true -> signToken(
                    target = TokenTarget.TOKEN,
                    payload = buildJsonObject {
                        put(JWTClaims.Payload.issuer, providerMetadata.issuer)
                        put(JWTClaims.Payload.audience, authorizationRequest.clientId)
                        put(JWTClaims.Payload.nonce, authorizationRequestServerNonce)
                        put("state", authServerState)
                        put("client_id", clientId)
                        put("redirect_uri", redirectUri)
                        put("response_type", getResponseTypeString(responseType))
                        put("response_mode", authorizationResponseServerMode.name)
                        put("scope", "openid")
                        when (responseType) {
                            ResponseType.VpToken -> put("presentation_definition", presentationDefinition!!.toJSON())
                            else -> {}
                        }
                    },
                    privKey = tokenKey
                )

                else -> null
            },
            presentationDefinition = when (responseType) {
                ResponseType.VpToken -> presentationDefinition!!.toJSONString()
                else -> null
            }
        )
    }

    suspend fun processCodeFlowAuthorization(
        authorizationRequest: AuthorizationRequest,
        sessionId: String,
        providerMetadata: OpenIDProviderMetadata,
        tokenKey: Key
    ): AuthorizationCodeResponse {
        if (!authorizationRequest.responseType.contains(ResponseType.Code))
            throw AuthorizationError(
                authorizationRequest = authorizationRequest,
                errorCode = AuthorizationErrorCode.invalid_request,
                message = "Invalid response type ${authorizationRequest.responseType}, for authorization code flow."
            )

        providerMetadata.draft11
            ?: providerMetadata.draft13
            ?: error("Unknown metadata type: $providerMetadata")

        val issuer = providerMetadata.issuer ?: throw AuthorizationError(
            authorizationRequest = authorizationRequest,
            errorCode = AuthorizationErrorCode.server_error,
            message = "No issuer configured in given provider metadata"
        )

        val code = generateAuthorizationCodeFor(
            sessionId = sessionId,
            issuer = issuer,
            tokenKey = tokenKey
        )

        return AuthorizationCodeResponse.success(
            code = code,
            customParameters = mapOf("state" to listOf(authorizationRequest.state ?: randomUUIDString()))
        )
    }

    suspend fun processImplicitFlowAuthorization(
        authorizationRequest: AuthorizationRequest,
        sessionId: String,
        providerMetadata: OpenIDProviderMetadata,
        tokenKey: Key
    ): TokenResponse {
        providerMetadata.draft11
            ?: providerMetadata.draft13
            ?: error("Unknown metadata type: $providerMetadata")

        log.debug { "> processImplicitFlowAuthorization for $authorizationRequest" }
        if (!authorizationRequest.responseType.contains(ResponseType.Token) && !authorizationRequest.responseType.contains(
                ResponseType.VpToken
            )
            && !authorizationRequest.responseType.contains(ResponseType.IdToken)
        )
            throw AuthorizationError(
                authorizationRequest = authorizationRequest,
                errorCode = AuthorizationErrorCode.invalid_request,
                message = "Invalid response type ${authorizationRequest.responseType}, for implicit authorization flow."
            )

        log.debug { "> processImplicitFlowAuthorization: generateTokenResponse..." }
        val issuer = providerMetadata.issuer ?: throw AuthorizationError(
            authorizationRequest = authorizationRequest,
            errorCode = AuthorizationErrorCode.server_error,
            message = "No issuer configured in given provider metadata"
        )
        return TokenResponse.success(
            accessToken = generateToken(
                sub = sessionId,
                issuer = issuer,
                audience = TokenTarget.ACCESS,
                tokenId = null,
                tokenKey = tokenKey
            ),
            tokenType = "bearer",
            state = authorizationRequest.state,
            expiresIn = Clock.System.now().epochSeconds + 864000L // ten days in seconds
        )
    }

    suspend fun processDirectPost(
        authorizationRequest: AuthorizationRequest,
        sessionId: String,
        providerMetadata: OpenIDProviderMetadata,
        tokenKey: Key
    ): AuthorizationCodeResponse {
        providerMetadata.draft11
            ?: providerMetadata.draft13
            ?: error("Unknown metadata type: $providerMetadata")

        // Verify nonce - need to add id token nonce session
        // if (payload[JWTClaims.Payload.nonce] != session.)

        // Generate code and proceed as regular authorization request
        val mappedState = mapOf("state" to listOf(authorizationRequest.state!!))

        val issuer = providerMetadata.issuer ?: throw AuthorizationError(
            authorizationRequest = authorizationRequest,
            errorCode = AuthorizationErrorCode.server_error,
            message = "No issuer configured in given provider metadata"
        )

        val code = generateAuthorizationCodeFor(
            sessionId = sessionId,
            issuer = issuer,
            tokenKey = tokenKey
        )

        return AuthorizationCodeResponse.success(
            code = code,
            customParameters = mappedState
        )
    }

    const val PUSHED_AUTHORIZATION_REQUEST_URI_PREFIX = "urn:ietf:params:oauth:request_uri:"

    fun getPushedAuthorizationRequestUri(sessionId: String): String =
        "$PUSHED_AUTHORIZATION_REQUEST_URI_PREFIX${sessionId}"

    fun getPushedAuthorizationSessionId(requestUri: String): String = requestUri.substringAfter(
        PUSHED_AUTHORIZATION_REQUEST_URI_PREFIX
    )

    // ------------------------------------------
    // Simple cryptographic operation interface implementations
    suspend fun signToken(
        target: TokenTarget,
        payload: JsonObject,
        privKey: Key,
        header: JsonObject? = null
    ): String {
        val keyId = privKey.getKeyId()
        log.debug { "Signing JWS:   $payload" }
        log.debug { "JWS Signature: target: $target, keyId: $keyId, header: $header" }

        val headers = (header?.toMutableMap() ?: mutableMapOf())
            .plus(
                mapOf(
                    JWTClaims.Header.algorithm to privKey.keyType.jwsAlg.toJsonElement(),
                    JWTClaims.Header.type to "jwt".toJsonElement(),
                    JWTClaims.Header.keyID to keyId.toJsonElement()
                )
            )

        return privKey.signJws(payload.toString().toByteArray(), headers).also {
            log.debug { "Signed JWS: >> $it" }
        }
    }

    fun signCWTToken(
        target: TokenTarget,
        payload: MapElement,
        privKey: Key,
        header: MapElement? = null,
    ): String {
        TODO("Not yet implemented, may not be required anymore (removed from https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html#cwt-proof-type)")
    }

    suspend fun verifyTokenSignature(target: TokenTarget, token: String, tokenKey: Key? = null): Boolean {
        log.debug { "Verifying JWS: $token" }
        log.debug { "JWS Verification: target: $target" }

        val tokenHeader = Json.parseToJsonElement(token.split(".")[0].base64UrlDecode().decodeToString()).jsonObject

        val key = when {
            tokenHeader["jwk"] != null -> JWKKey.importJWK(tokenHeader["jwk"].toString()).getOrThrow()

            tokenHeader[JWTClaims.Header.keyID] != null -> {
                val kid = tokenHeader[JWTClaims.Header.keyID]!!.jsonPrimitive.content.split("#")[0]
                when {
                    DidUtils.isDidUrl(kid) -> {
                        log.debug { "Resolving DID: $kid" }
                        DidService.resolveToKey(kid).getOrThrow()
                    }

                    tokenKey != null && kid == tokenKey.getKeyId() -> tokenKey
                    else -> null
                }
            }

            else -> tokenKey
        }
            ?: throw TokenVerificationError(
                token = token,
                target = target,
                message = "Could not resolve key for given token"
            )

        return key.verifyJws(token).also { log.debug { "VERIFICATION IS: $it" } }.isSuccess
    }

    fun verifyCOSESign1Signature(
        target: TokenTarget,
        token: String
    ): Boolean {
        // May not be required anymore (removed from https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html#cwt-proof-type)
        log.debug { "Verifying JWS: $token" }
        log.debug { "JWS Verification: target: $target" }
        // requires currently JVM specific implementation for COSE_Sign1 signature verification
        return COSESign1Utils.verifyCOSESign1Signature(target, token)
    }

    private fun parseTokenPayload(token: String): JsonObject {
        return token.substringAfter(".").substringBefore(".").let {
            Json.decodeFromString(it.decodeFromBase64Url().decodeToString())
        }
    }

    private fun parseTokenHeader(token: String): JsonObject {
        return token.substringBefore(".").let {
            Json.decodeFromString(it.decodeFromBase64Url().decodeToString())
        }
    }
}
