package id.walt.openid4vci.granttypehandlers

import id.walt.openid4vci.AuthorizeEndpointHandler
import id.walt.openid4vci.Session
import id.walt.openid4vci.TokenType
import id.walt.openid4vci.core.AuthorizeResponse
import id.walt.openid4vci.core.AuthorizeResponseResult
import id.walt.openid4vci.core.OAuthError
import id.walt.openid4vci.repository.authorization.AuthorizationCodeRecord
import id.walt.openid4vci.repository.authorization.AuthorizationCodeRepository
import id.walt.openid4vci.request.AuthorizationRequest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

/**
 * Authorize endpoint handler that issues authorization codes. The handler:
 * 1. Grants requested scopes/audience (placeholder until consent is wired).
 * 2. Generates a code and persists the session via [AuthorizationCodeRepository].
 * 3. Returns redirect parameters containing the code and state.
 */
class AuthorizationCodeAuthorizeHandler(
    private val codeRepository: AuthorizationCodeRepository,
    private val codeLifetimeSeconds: Long = 300,
) : AuthorizeEndpointHandler {

    @OptIn(ExperimentalTime::class)
    override suspend fun handleAuthorizeEndpointRequest(request: AuthorizationRequest, session: Session): AuthorizeResponseResult {
        if (!request.getResponseTypes().contains("code")) {
            return AuthorizeResponseResult.Failure(
                OAuthError("unsupported_response_type", "Handler only supports response_type=code"),
            )
        }

        val redirectUri = request.redirectUri
            ?: request.getClient().redirectUris.firstOrNull()
            ?: return AuthorizeResponseResult.Failure(
                OAuthError("invalid_request", "Client is missing redirect_uri"),
            )

        request.redirectUri = redirectUri

        val issuerId = request.getIssuerId()
            ?: return AuthorizeResponseResult.Failure(
                OAuthError("invalid_request", "Issuer context missing"),
            )

        // For the skeleton we auto-grant everything that was requested and mark the response type as handled,
        // mirroring the behaviour expected once consent is complete.
        request.getRequestedScopes().forEach { request.grantScope(it) }
        request.getRequestedAudience().forEach { request.grantAudience(it) }
        request.getResponseTypes().forEach { request.setResponseTypeHandled(it) }

        val code = generateCode()
        val expiresAt = kotlin.time.Clock.System.now() + codeLifetimeSeconds.seconds

        codeRepository.save(
            AuthorizationCodeRecord(
                code = code,
                clientId = request.getClient().id,
                redirectUri = redirectUri,
                grantedScopes = request.getGrantedScopes().toSet(),
                grantedAudience = request.getGrantedAudience().toSet(),
                session = session.cloneSession().apply {
                    setExpiresAt(TokenType.AUTHORIZATION_CODE, expiresAt)
                },
                expiresAt = expiresAt,
            ),
            issuerId,
        )

        val parameters = buildMap {
            put("code", code)
            request.state?.let { put("state", it) }
            val grantedScope = request.getGrantedScopes().toSet()
            if (grantedScope.isNotEmpty()) {
                put("scope", grantedScope.joinToString(" "))
            }
        }

        return AuthorizeResponseResult.Success(
            AuthorizeResponse(
                redirectUri = redirectUri,
                parameters = parameters,
            ),
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun generateCode(): String {
        val bytes = Random.nextBytes(33) //to prevent padding
        return Base64.UrlSafe.encode(bytes)
    }
}
