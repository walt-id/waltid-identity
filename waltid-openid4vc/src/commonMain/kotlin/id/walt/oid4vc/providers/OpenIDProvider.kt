package id.walt.oid4vc.providers

import id.walt.oid4vc.data.GrantType
import id.walt.oid4vc.data.OpenIDProviderMetadata
import id.walt.oid4vc.data.ResponseType
import id.walt.oid4vc.data.SubjectType
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
        credentialIssuer = "$baseUrl/.well-known/openid-credential-issuer"
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

    abstract fun initializeAuthorization(authorizationRequest: AuthorizationRequest, expiresIn: Duration): S
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
        return TokenResponse.success(
            generateToken(session.id, TokenTarget.ACCESS),
            "bearer", state = session.authorizationRequest?.state
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

    protected open fun getOrInitAuthorizationSession(authorizationRequest: AuthorizationRequest): S {
        return when (authorizationRequest.isReferenceToPAR) {
            true -> getPushedAuthorizationSession(authorizationRequest)
            false -> initializeAuthorization(authorizationRequest, 5.minutes)
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
