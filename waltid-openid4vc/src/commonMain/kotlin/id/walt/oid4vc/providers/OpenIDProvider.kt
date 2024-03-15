package id.walt.oid4vc.providers

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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.uuid.UUID

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

    open fun processDirectPost(state: String) : AuthorizationCodeResponse {

        println("Incoming State is $state")
        // here we get the initial session to retrieve the state of the initial authorization request
        val session = getSessionByIdTokenRequestState(state)
        println("Session Id is: ${session?.id}")
        println("Session Authorization Request State is: ${session?.authorizationRequest?.state}")
        println("Session Id Token Request State is: ${session?.idTokenRequestState}")

        // Generate code and proceed as regular authorization request
        val mappedState = mapOf("state" to listOf(session?.authorizationRequest?.state!!))
        val code = generateAuthorizationCodeFor(session)
        return AuthorizationCodeResponse.success(code, mappedState)
    }

    open fun processCodeFlowAuthorizationEbsi(authorizationRequest: AuthorizationRequest, keyId: String): AuthorizationCodeIDTokenRequestResponse {
        println("Ebsi Authorize Request")

        if (!authorizationRequest.responseType.contains(ResponseType.Code))
            throw AuthorizationError(
                authorizationRequest,
                AuthorizationErrorCode.invalid_request,
                message = "Invalid response type ${authorizationRequest.responseType}, for authorization code flow."
            )

        // Create ID Token Request

        // Bind authentication request with state
        // @see https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest
        // `state`: RECOMMENDED. Opaque value used to maintain state between the request and the
        // callback. Typically, Cross-Site Request Forgery (CSRF, XSRF) mitigation is done by
        // cryptographically binding the value of this parameter with a browser cookie.
        val idTokenRequestState = UUID().toString();
        val idTokenRequestNonce = UUID().toString();
        val responseMode = ResponseMode.DirectPost

        val clientId = this.metadata.issuer!! // :/
        val redirectUri = this.metadata.issuer + "/direct_post"
        val responseType = "id_token"
        val scope = setOf("openid") // How to put the array of scopes in the token above?

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
                put("scope", "openid") // How can we put an array of scopes?
            }, buildJsonObject {
                put(JWTClaims.Header.algorithm, "ES256")
                put(JWTClaims.Header.keyID, keyId)
                put(JWTClaims.Header.type, "jwt")
            },
            keyId
        )

        // Create a session with the state of id token request since it is needed in the direct_post endpoint
        val authorizationSession = initializeAuthorization(authorizationRequest, 5.minutes, idTokenRequestState)

        println("Authorization Session Id is: ${authorizationSession.id}")
        println("Authorization State is: ${authorizationSession.authorizationRequest?.state}")
        println("Authorization Id Token Request State is: ${authorizationSession.idTokenRequestState}")
        println("Id Token Request State: $idTokenRequestState")
        println("Id Token Request Nonce: $idTokenRequestNonce")
        println("JAR Token is: $requestJar")

        return AuthorizationCodeIDTokenRequestResponse.success(idTokenRequestState, clientId, redirectUri, responseType, responseMode, scope, idTokenRequestNonce, null,  requestJar)
    }

    open fun processImplicitFlowAuthorization(authorizationRequest: AuthorizationRequest): TokenResponse {
        println("> processImplicitFlowAuthorization for $authorizationRequest")
        if (!authorizationRequest.responseType.contains(ResponseType.Token) && !authorizationRequest.responseType.contains(ResponseType.VpToken))
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
