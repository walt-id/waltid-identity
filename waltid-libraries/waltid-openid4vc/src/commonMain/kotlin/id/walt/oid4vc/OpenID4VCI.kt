package id.walt.oid4vc

import id.walt.credentials.verification.Verifier
import id.walt.credentials.verification.models.PolicyRequest.Companion.parsePolicyRequests
import id.walt.crypto.keys.Key
import id.walt.oid4vc.data.*
import id.walt.oid4vc.data.ResponseType.Companion.getResponseTypeString
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.data.dif.PresentationSubmission
import id.walt.oid4vc.definitions.CROSS_DEVICE_CREDENTIAL_OFFER_URL
import id.walt.oid4vc.definitions.JWTClaims
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.CredentialOfferRequest
import id.walt.oid4vc.requests.TokenRequest
import id.walt.oid4vc.responses.TokenErrorCode
import id.walt.oid4vc.errors.TokenError
import id.walt.oid4vc.responses.AuthorizationCodeWithAuthorizationRequestResponse
import id.walt.oid4vc.util.JwtUtils
import id.walt.oid4vc.util.http
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.json.*
import kotlinx.serialization.json.Json

object OpenID4VCI {
    fun getCredentialOfferRequestUrl(
        credOffer: CredentialOffer,
        credentialOfferEndpoint: String = CROSS_DEVICE_CREDENTIAL_OFFER_URL,
        customReqParams: Map<String, List<String>> = mapOf()
    ): String {
        return URLBuilder(credentialOfferEndpoint).apply {
            parameters.appendAll(parametersOf(CredentialOfferRequest(credOffer, customParameters = customReqParams).toHttpParameters()))
        }.buildString()
    }

    fun getCredentialOfferRequestUrl(
        credOfferUrl: String,
        credentialOfferEndpoint: String = CROSS_DEVICE_CREDENTIAL_OFFER_URL,
        customReqParams: Map<String, List<String>> = mapOf()
    ): String {
        return URLBuilder(credentialOfferEndpoint).apply {
            parameters.appendAll(
                parametersOf(
                    CredentialOfferRequest(
                        credentialOfferUri = credOfferUrl,
                        customParameters = customReqParams
                    ).toHttpParameters()
                )
            )
        }.buildString()
    }

    fun parseCredentialOfferRequestUrl(credOfferReqUrl: String): CredentialOfferRequest {
        return CredentialOfferRequest.fromHttpParameters(Url(credOfferReqUrl).parameters.toMap())
    }

    suspend fun parseAndResolveCredentialOfferRequestUrl(credOfferReqUrl: String): CredentialOffer {
        val offerReq = parseCredentialOfferRequestUrl(credOfferReqUrl)
        return offerReq.credentialOffer
            ?: if (!offerReq.credentialOfferUri.isNullOrEmpty()) {

                http.get(offerReq.credentialOfferUri).bodyAsText().let {
                    CredentialOffer.fromJSONString(it)
                }
            } else throw Exception("Credential offer request has no credential offer object set by value or reference.")
    }

    fun getCIProviderMetadataUrl(credOffer: CredentialOffer): String {
        return getCIProviderMetadataUrl(credOffer.credentialIssuer)
    }

    fun getCIProviderMetadataUrl(baseUrl: String) = URLBuilder(baseUrl).apply {
            appendPathSegments(".well-known", "openid-credential-issuer")
        }.buildString()

    fun getCommonProviderMetadataUrl(baseUrl: String) = URLBuilder(baseUrl).apply {
            appendPathSegments(".well-known", "openid-configuration")
        }.buildString()

    fun getOAuthProviderMetadataUrl(baseUrl: String) = URLBuilder(baseUrl).apply {
            appendPathSegments(".well-known", "oauth-authorization-server")
        }.buildString()

    fun getJWTIssuerProviderMetadataUrl(baseUrl: String) = URLBuilder(baseUrl).apply {
            appendPathSegments(".well-known", "jwt-issuer")
        }.buildString()

    suspend fun resolveCIProviderMetadata(credOffer: CredentialOffer) = http.get(getCIProviderMetadataUrl(credOffer)).bodyAsText().let {
        OpenIDProviderMetadata.fromJSONString(it)
    }

    fun resolveOfferedCredentials(credentialOffer: CredentialOffer, providerMetadata: OpenIDProviderMetadata): List<OfferedCredential> {
        val supportedCredentials =
            providerMetadata.credentialConfigurationsSupported ?: mapOf()
        return credentialOffer.credentialConfigurationIds.mapNotNull { c ->
            supportedCredentials[c]?.let {
                OfferedCredential.fromProviderMetadata(it)
            }
        }
    }

    fun validateAuthorizationRequestQueryString(authorizationRequestQueryString: String): AuthorizationRequest {
        // Todo validate authorization request params e.g. client_id, etc
        return parseAuthorizationRequestQueryString(authorizationRequestQueryString)
    }

    private fun parseAuthorizationRequestQueryString(authorizationRequestQueryString: String): AuthorizationRequest {
        return AuthorizationRequest.fromHttpQueryString(authorizationRequestQueryString)
    }

    fun validateTokenRequestRaw(tokenRequestRaw: Map<String, List<String>>, authorizationCode: String): TokenRequest {
        val tokenRequest = parseTokenRequest(tokenRequestRaw)
        validateAuthorizationCode(tokenRequest, authorizationCode).let {
            if (!it) throw TokenError(tokenRequest, TokenErrorCode.invalid_request, "Code is not valid")
        }
        return tokenRequest
    }

    private fun parseTokenRequest(tokenRequestRaw: Map<String, List<String>>): TokenRequest {
        return TokenRequest.fromHttpParameters(tokenRequestRaw)
    }

    private fun validateAuthorizationCode(tokenRequest: TokenRequest, authorizationCode: String): Boolean {
        val code = when (tokenRequest.grantType) {
            GrantType.authorization_code -> tokenRequest.code ?: throw TokenError(
                tokenRequest = tokenRequest,
                errorCode = TokenErrorCode.invalid_grant,
                message = "No code parameter found on token request"
            )

            GrantType.pre_authorized_code -> tokenRequest.preAuthorizedCode ?: throw TokenError(
                tokenRequest = tokenRequest,
                errorCode = TokenErrorCode.invalid_grant,
                message = "No pre-authorized_code parameter found on token request"
            )

            else -> throw TokenError(tokenRequest, TokenErrorCode.unsupported_grant_type, "Grant type not supported")
        }
        return code == authorizationCode
    }

    suspend fun signToken(privateKey: Key, payload: JsonObject, headers: Map<String, JsonElement>? = null): String {
        return privateKey.signJws(payload.toString().toByteArray(), headers ?: emptyMap())
    }

    suspend fun verifyToken(token: String, key: Key): Result<JsonElement> {
        return key.verifyJws(token)
    }

    suspend fun generateAuthorizationRequest(
        authorizationRequest: AuthorizationRequest,
        clientId: String,
        privKey: Key,
        responseType: ResponseType,
        state: String,
        nonce: String,
        isJar: Boolean? = true,
        presentationDefinition: PresentationDefinition? = null
    ): AuthorizationCodeWithAuthorizationRequestResponse {
        val authorizationResponseServerMode = ResponseMode.direct_post
        val redirectUri = clientId + "/direct_post"
        val scope = setOf("openid")

        return AuthorizationCodeWithAuthorizationRequestResponse.success(
            state = state,
            clientId = clientId,
            redirectUri = redirectUri,
            responseType = getResponseTypeString(responseType),
            responseMode = authorizationResponseServerMode,
            scope = scope,
            nonce = nonce,
            requestUri = null,
            request = when (isJar!!) {
                // Create a jwt as request object as defined in JAR OAuth2.0 specification
                true -> signToken(
                    privKey,
                    buildJsonObject {
                        put(JWTClaims.Payload.issuer, clientId)
                        put(JWTClaims.Payload.audience, authorizationRequest.clientId)
                        put(JWTClaims.Payload.nonce, nonce)
                        put("state", state)
                        put("client_id", clientId)
                        put("redirect_uri", redirectUri)
                        put("response_type", getResponseTypeString(responseType))
                        put("response_mode", authorizationResponseServerMode.name)
                        put("scope", "openid")
                        when (responseType) {
                            ResponseType.VpToken -> put("presentation_definition", presentationDefinition!!.toJSON())
                            else -> null
                        }
                    }
                )
                false -> null
            },
            presentationDefinition = when (responseType) {
                ResponseType.VpToken -> presentationDefinition!!.toJSONString()
                else -> null
            }
        )
    }

    suspend fun validateAuthorizationRequestToken(
        token: String,
        presentationSubmission: PresentationSubmission? = null
    ): JsonObject {
        // 1. Validate Header
        val header = JwtUtils.parseJWTHeader(token)

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
        val payload = JwtUtils.parseJWTPayload(token)
        if (!payload.keys.containsAll(
                setOf(
                    JWTClaims.Payload.issuer,
                    JWTClaims.Payload.audience,
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
            println("$sub $iss $did")
            throw IllegalArgumentException("Invalid payload in token. sub != iss != did")
        }

        // 4. Verify VP or ID Token
        val policies =
            Json.parseToJsonElement("""["signature", "expired", "not-before"]""").jsonArray.parsePolicyRequests()
        Verifier.verifyPresentation(
            vpTokenJwt = token,
            vpPolicies = policies,
            globalVcPolicies = policies,
            specificCredentialPolicies = emptyMap(),
            when (presentationSubmission != null) {
                true -> mapOf("presentationSubmission" to presentationSubmission)
                else -> emptyMap()
            }
        )

        return payload
    }
}
