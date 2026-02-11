package id.walt.openid4vci.granttypehandlers

import id.walt.openid4vci.GrantType
import id.walt.openid4vci.TokenEndpointHandler
import id.walt.openid4vci.TokenEndpointResult
import id.walt.openid4vci.repository.authorization.AuthorizationCodeRepository
import id.walt.openid4vci.request.AccessTokenRequest
import id.walt.openid4vci.tokens.AccessTokenService
import id.walt.openid4vci.tokens.jwt.defaultAccessTokenClaims
import kotlin.time.Clock

/**
 * Token endpoint handler for the authorization-code code grant.
 *
 * Returns a lightweight [TokenEndpointResult] instead of mutating a responder in place.
 */
    class AuthorizationCodeTokenHandler(
        private val codeRepository: AuthorizationCodeRepository,
        private val tokenService: AccessTokenService,
    ) : TokenEndpointHandler {

    override fun canHandleTokenEndpointRequest(request: AccessTokenRequest): Boolean =
        request.getGrantTypes().contains(GrantType.AuthorizationCode.value)

    override suspend fun handleTokenEndpointRequest(request: AccessTokenRequest): TokenEndpointResult {
        if (!canHandleTokenEndpointRequest(request)) {
            return TokenEndpointResult.Failure("unsupported_grant_type", "authorization_code grant not requested")
        }

        val code = request.getRequestForm().getFirst("code")
            ?: return TokenEndpointResult.Failure("invalid_request", "Missing authorization code")

        val record = codeRepository.consume(code)
            ?: return TokenEndpointResult.Failure("invalid_grant", "Authorization code is invalid or has already been used")

        val client = request.getClient()
        if (client.id != record.clientId) {
            return TokenEndpointResult.Failure("invalid_grant", "Client mismatch for authorization code")
        }

        val redirectParam = request.getRequestForm().getFirst("redirect_uri")
        if (record.redirectUri != null) {
            // RFC6749 §4.1.3: redirect_uri is REQUIRED here only if the authorize request carried it and must match verbatim.
            if (redirectParam.isNullOrBlank() || record.redirectUri != redirectParam) {
                return TokenEndpointResult.Failure("invalid_grant", "redirect_uri does not match authorization request")
            }
        }

        // Restore session, scopes, and audience from the original authorize request.
        request.setSession(record.session.cloneSession())
        request.markGrantTypeHandled(GrantType.AuthorizationCode.value)
        record.grantedScopes.forEach(request::grantScope)
        record.grantedAudience.forEach(request::grantAudience)

        val expiresAt = request.getSession()?.getExpiresAt(id.walt.openid4vci.TokenType.ACCESS_TOKEN)
            ?: Clock.System.now()

        val subject = request.getSession()?.getSubject()?.takeIf { it.isNotBlank() }
            ?: return TokenEndpointResult.Failure("invalid_request", "subject is required in session")

        val claims = defaultAccessTokenClaims(
            subject = subject,
            issuer = request.getIssuerId() ?: client.id,
            audience = request.getGrantedAudience().toSet().firstOrNull(),
            scopes = request.getGrantedScopes().toSet(),
            expiresAt = expiresAt,
            additional = mapOf(
                "client_id" to client.id,
            ),
        )

        val accessToken = tokenService.createAccessToken(claims)

        return TokenEndpointResult.Success(
            accessToken = accessToken,
        )
    }
}
