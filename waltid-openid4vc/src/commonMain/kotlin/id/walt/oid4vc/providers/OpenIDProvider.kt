package id.walt.oid4vc.providers

import id.walt.crypto.keys.Key
import id.walt.oid4vc.data.*
import id.walt.oid4vc.definitions.JWTClaims
import id.walt.oid4vc.errors.AuthorizationError
import id.walt.oid4vc.errors.TokenError
import id.walt.oid4vc.interfaces.ISessionCache
import id.walt.oid4vc.interfaces.ITokenProvider
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.TokenRequest
import id.walt.oid4vc.responses.*

import io.ktor.http.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.uuid.UUID
import kotlin.js.ExperimentalJsExport

abstract class OpenIDProvider<S : AuthorizationSession>(
    val baseUrl: String,
) : ISessionCache<S>, ITokenProvider {
    abstract val metadata: OpenIDProviderMetadata
    abstract val config: OpenIDProviderConfig

    protected open fun createDefaultProviderMetadata() = OpenIDProviderMetadata(
        issuer = baseUrl,
        authorizationEndpoint = "$baseUrl/authorize",
        pushedAuthorizationRequestEndpoint = "$baseUrl/par",
        tokenEndpoint = "$baseUrl/token",
        credentialEndpoint = "$baseUrl/credential",
        batchCredentialEndpoint = "$baseUrl/batch_credential",
        deferredCredentialEndpoint = "$baseUrl/credential_deferred",
        jwksUri = "$baseUrl/jwks",
        grantTypesSupported = setOf(GrantType.authorization_code, GrantType.pre_authorized_code),
        requestUriParameterSupported = true,
        subjectTypesSupported = setOf(SubjectType.public),
        authorizationServer = baseUrl,
        credentialIssuer =  baseUrl, // (EBSI) this should be just "$baseUrl"  https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html#section-11.2.1
        responseTypesSupported = setOf("code", "vp_token", "id_token"),  // (EBSI) this is required one  https://www.rfc-editor.org/rfc/rfc8414.html#section-2
        idTokenSigningAlgValuesSupported = setOf("ES256") // (EBSI) https://openid.net/specs/openid-connect-self-issued-v2-1_0.html#name-self-issued-openid-provider-
    )

    fun getCommonProviderMetadataUrl(): String {
        return URLBuilder(baseUrl).apply {
            pathSegments = listOf(".well-known", "openid-configuration")
        }.buildString()
    }

    protected open fun generateToken(sub: String, audience: TokenTarget, tokenId: String? = null): String {
        return signToken(audience, buildJsonObject {
            put(JWTClaims.Payload.subject, sub)
            put(JWTClaims.Payload.issuer, metadata.issuer)
            put(JWTClaims.Payload.audience, audience.name)
            tokenId?.let { put(JWTClaims.Payload.jwtID, it) }
        })
    }

    protected open fun verifyAndParseToken(token: String, target: TokenTarget): JsonObject? {
        if (verifyTokenSignature(target, token)) {
            val payload = parseTokenPayload(token)
            if (payload.keys.containsAll(
                    setOf(
                        JWTClaims.Payload.subject,
                        JWTClaims.Payload.audience,
                        JWTClaims.Payload.issuer
                    )
                ) &&
                payload[JWTClaims.Payload.audience]!!.jsonPrimitive.content == target.name &&
                payload[JWTClaims.Payload.issuer]!!.jsonPrimitive.content == metadata.issuer
            ) {
                return payload
            }
        }
        return null
    }

    protected open fun generateAuthorizationCodeFor(session: S): String {
        return generateToken(session.id, TokenTarget.TOKEN)
    }

    protected open fun validateAuthorizationCode(code: String): JsonObject? {
        return verifyAndParseToken(code, TokenTarget.TOKEN)
    }

    @OptIn(ExperimentalJsExport::class)
    protected open fun verifyAndParseIdToken(token: String): JsonObject? {
        // 1. Validate Header
        val header = parseTokenHeader(token)
        if (!header.keys.containsAll(setOf(
                JWTClaims.Header.type,
                JWTClaims.Header.keyID,
                JWTClaims.Header.algorithm,
            )
            )){
            throw IllegalStateException( "Invalid header in token")
        }

        // 2. Validate Payload
        val payload = parseTokenPayload(token)
        if (!payload.keys.containsAll(setOf(
                JWTClaims.Payload.issuer,
                JWTClaims.Payload.subject,
                JWTClaims.Payload.audience,
                JWTClaims.Payload.expirationTime,
                JWTClaims.Payload.issuedAtTime,
                JWTClaims.Payload.nonce,
            )
            )){
            throw IllegalArgumentException("Invalid payload in token")
        }

        // 3. Verify iss = sub = did
        val sub = payload[JWTClaims.Payload.subject]!!.jsonPrimitive.content
        val iss = payload[JWTClaims.Payload.issuer]!!.jsonPrimitive.content
        val kid = header[JWTClaims.Header.keyID]!!.jsonPrimitive.content
        val did = kid.substringBefore("#")

        if (iss != sub || iss != did || sub != did){
            println("$sub $iss $did")
            throw IllegalArgumentException("Invalid payload in token. sub != iss != did")
        }

        // 4. Verify Signature

        // 4.a Resolve DID
        // DidService.minimalInit()
        // val didDocument = DidService.resolve(did.removeSurrounding("\"")
        //)

        // 4.b Get verification methods from DID Document
        // val verificationMethods = didDocument.getOrNull()?.get("verificationMethod")

        // 4.c Get the corresponding verification method
        // val verificationMethod = verificationMethods?.jsonArray?.firstOrNull {
        //    it.jsonObject["id"].toString() == kidFull.toString()
        //} ?: throw IllegalArgumentException("Invalid verification method")

        // 4.d Verify Token
        // val key = DidService.resolveToKey(did).getOrThrow()
        // key.verifyJws(token).isSuccess

        if (!verifyTokenSignature(TokenTarget.TOKEN,token))
            throw IllegalArgumentException("Invalid token - cannot verify signature")

        return payload
    }

    protected abstract fun validateAuthorizationRequest(authorizationRequest: AuthorizationRequest): Boolean

    abstract fun initializeAuthorization(authorizationRequest: AuthorizationRequest, expiresIn: Duration, idTokenRequestState: String? ): S

    open fun processCodeFlowAuthorization(authorizationRequest: AuthorizationRequest): AuthorizationCodeResponse {
        if (!authorizationRequest.responseType.contains(ResponseType.Code))
            throw AuthorizationError(
                authorizationRequest,
                AuthorizationErrorCode.invalid_request,
                message = "Invalid response type ${authorizationRequest.responseType}, for authorization code flow."
            )
        val authorizationSession = getOrInitAuthorizationSession(authorizationRequest)
        val code = generateAuthorizationCodeFor(authorizationSession)
        return AuthorizationCodeResponse.success(code)
    }

    open fun processDirectPost(state: String, tokenPayload: JsonObject) : AuthorizationCodeResponse {
        // here we get the initial session to retrieve the state of the initial authorization request
        val session = getSessionByIdTokenRequestState(state)
            ?: throw IllegalStateException( "No authentication request found for given state")

        // Verify nonce - need to add Id token nonce session
        // if (payload[JWTClaims.Payload.nonce] != session.)

        // Generate code and proceed as regular authorization request
        val mappedState = mapOf("state" to listOf(session.authorizationRequest?.state!!))
        val code = generateAuthorizationCodeFor(session)

        return AuthorizationCodeResponse.success(code, mappedState)
    }

    // TO-DO: JAR OAuth2.0 specification https://www.rfc-editor.org/rfc/rfc9101.html
    // open fun proccessJar(authorizationRequest: AuthorizationRequest, kid: String){
    // }

    // Create an ID Token request using JAR OAuth2.0 specification https://www.rfc-editor.org/rfc/rfc9101.html
    open fun processCodeFlowAuthorizationWithIdTokenRequest(authorizationRequest: AuthorizationRequest, keyId: String, privKey: Key): AuthorizationCodeIDTokenRequestResponse {
        if (!authorizationRequest.responseType.contains(ResponseType.Code))
            throw AuthorizationError(
                authorizationRequest,
                AuthorizationErrorCode.invalid_request,
                message = "Invalid response type ${authorizationRequest.responseType}, for authorization code flow."
            )

        // Bind authentication request with state
        val idTokenRequestState = UUID().toString();
        val idTokenRequestNonce = UUID().toString();
        val responseMode = ResponseMode.direct_post

        val clientId = this.metadata.issuer!!
        val redirectUri = this.metadata.issuer + "/direct_post"
        val responseType = "id_token"
        val scope = setOf("openid")

        // Create a jwt as request object as defined in JAR OAuth2.0 specification
        val requestJar = signToken (
            TokenTarget.TOKEN,
            buildJsonObject {
                put(JWTClaims.Payload.issuer, metadata.issuer)
                put(JWTClaims.Payload.audience, authorizationRequest.clientId)
                put(JWTClaims.Payload.nonce, idTokenRequestNonce)
                put("state", idTokenRequestState)
                put("client_id", clientId)
                put("redirect_uri", redirectUri)
                put("response_type", responseType)
                put("response_mode", responseMode.name)
                put("scope", "openid")
            }, buildJsonObject {
                put(JWTClaims.Header.algorithm, "ES256")
                put(JWTClaims.Header.keyID, keyId)
                put(JWTClaims.Header.type, "jwt")
            },
            keyId,
            privKey
        )

        // Create a session with the state of the ID Token request since it is needed in the direct_post endpoint
        initializeAuthorization(authorizationRequest, 5.minutes, idTokenRequestState)

        return AuthorizationCodeIDTokenRequestResponse.success(idTokenRequestState, clientId, redirectUri, responseType, responseMode, scope, idTokenRequestNonce, null,  requestJar)
    }

    open fun processImplicitFlowAuthorization(authorizationRequest: AuthorizationRequest): TokenResponse {
        println("> processImplicitFlowAuthorization for $authorizationRequest")
        if (!authorizationRequest.responseType.contains(ResponseType.Token) && !authorizationRequest.responseType.contains(ResponseType.VpToken)
            && !authorizationRequest.responseType.contains(ResponseType.IdToken))
            throw AuthorizationError(
                authorizationRequest,
                AuthorizationErrorCode.invalid_request,
                message = "Invalid response type ${authorizationRequest.responseType}, for implicit authorization flow."
            )
        println("> processImplicitFlowAuthorization: Generating authorizationSession (getOrInitAuthorizationSession)...")
        val authorizationSession = getOrInitAuthorizationSession(authorizationRequest)
        println("> processImplicitFlowAuthorization: generateTokenResponse...")
        return generateTokenResponse(
            authorizationSession,
            TokenRequest(GrantType.implicit, authorizationRequest.clientId)
        )
    }

    protected open fun generateTokenResponse(session: S, tokenRequest: TokenRequest): TokenResponse {
        // Expiration time required by EBSI
        val currentTime = Clock.System.now().epochSeconds
        val expirationTime = (currentTime + 864000L) // ten days in milliseconds
        return TokenResponse.success(
            generateToken(session.id, TokenTarget.ACCESS),
            "bearer", state = session.authorizationRequest?.state,
            expiresIn = expirationTime
        )
    }

    protected fun getVerifiedSession(sessionId: String): S? {
        return getSession(sessionId)?.let {
            if (it.isExpired) {
                removeSession(sessionId)
                null
            } else {
                it
            }
        }
    }

    open fun processTokenRequest(tokenRequest: TokenRequest): TokenResponse {
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
        val payload = validateAuthorizationCode(code) ?: throw TokenError(
            tokenRequest = tokenRequest,
            errorCode = TokenErrorCode.invalid_grant,
            message = "Authorization code could not be verified"
        )

        val sessionId = payload["sub"]!!.jsonPrimitive.content
        val session = getVerifiedSession(sessionId) ?: throw TokenError(
            tokenRequest = tokenRequest,
            errorCode = TokenErrorCode.invalid_request,
            message = "No authorization session found for given authorization code, or session expired."
        )

        return generateTokenResponse(session, tokenRequest)
    }

    fun getPushedAuthorizationSuccessResponse(authorizationSession: S) = PushedAuthorizationResponse.success(
        requestUri = "urn:ietf:params:oauth:request_uri:${authorizationSession.id}",
        expiresIn = authorizationSession.expirationTimestamp - Clock.System.now()
    )

    protected open fun getOrInitAuthorizationSession(authorizationRequest: AuthorizationRequest, idTokenRequestState: String?=null): S {
        return when (authorizationRequest.isReferenceToPAR) {
            true -> getPushedAuthorizationSession(authorizationRequest)
            false -> initializeAuthorization(authorizationRequest, 5.minutes, idTokenRequestState)
        }
    }

    fun getPushedAuthorizationSession(authorizationRequest: AuthorizationRequest): S {
        val session = authorizationRequest.requestUri?.let {
            getVerifiedSession(
                it.substringAfter("urn:ietf:params:oauth:request_uri:")
            ) ?: throw AuthorizationError(
                authorizationRequest,
                AuthorizationErrorCode.invalid_request,
                "No session found for given request URI, or session expired"
            )
        } ?: throw AuthorizationError(
            authorizationRequest,
            AuthorizationErrorCode.invalid_request,
            "Authorization request does not refer to a pushed authorization session"
        )

        return session
    }

    fun validateAccessToken(accessToken: String): Boolean {
        return verifyAndParseToken(accessToken, TokenTarget.ACCESS) != null
    }

}
