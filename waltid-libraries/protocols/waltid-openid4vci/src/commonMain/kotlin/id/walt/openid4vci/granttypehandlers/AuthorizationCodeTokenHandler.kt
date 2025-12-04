package id.walt.openid4vci.granttypehandlers

import id.walt.openid4vci.GRANT_TYPE_AUTHORIZATION_CODE
import id.walt.openid4vci.TokenEndpointHandler
import id.walt.openid4vci.TokenEndpointResult
import id.walt.openid4vci.repository.authorization.AuthorizationCodeRepository
import id.walt.openid4vci.request.AccessTokenRequest
import id.walt.openid4vci.tokens.TokenService

/**
 * Token endpoint handler for the authorization-code/preauthorized code grant.
 *
 * Returns a lightweight [TokenEndpointResult] instead of mutating a responder in place.
 */
class AuthorizationCodeTokenHandler(
    private val codeRepository: AuthorizationCodeRepository,
    private val tokenService: TokenService = TokenService(),
) : TokenEndpointHandler {

    override fun canHandleTokenEndpointRequest(request: AccessTokenRequest): Boolean =
        request.getGrantTypes().contains(GRANT_TYPE_AUTHORIZATION_CODE)

    override fun handleTokenEndpointRequest(request: AccessTokenRequest): TokenEndpointResult {
        if (!canHandleTokenEndpointRequest(request)) {
            return TokenEndpointResult.Failure("unsupported_grant_type", "authorization_code grant not requested")
        }

        val code = request.getRequestForm().getFirst("code")
            ?: return TokenEndpointResult.Failure("invalid_request", "Missing authorization code")

        val issuerId = request.getIssuerId()
            ?: return TokenEndpointResult.Failure("invalid_request", "Issuer context missing")

        val record = codeRepository.consume(code, issuerId)
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
        request.markGrantTypeHandled(GRANT_TYPE_AUTHORIZATION_CODE)
        record.grantedScopes.forEach(request::grantScope)
        record.grantedAudience.forEach(request::grantAudience)

        val accessToken = tokenService.createAccessToken(client.id, code)

        return TokenEndpointResult.Success(
            accessToken = accessToken,
        )
    }
}
