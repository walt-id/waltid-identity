package id.walt.openid4vci.handlers.granttypes.authorizationcode

import id.walt.openid4vci.GrantType
import id.walt.openid4vci.handlers.token.TokenEndpointHandler
import id.walt.openid4vci.responses.token.AccessResponseResult
import id.walt.openid4vci.responses.token.AccessTokenResponse
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.repository.authorization.AuthorizationCodeRepository
import id.walt.openid4vci.requests.token.AccessTokenRequest
import id.walt.openid4vci.tokens.AccessTokenService
import id.walt.openid4vci.tokens.jwt.defaultAccessTokenClaims
import kotlinx.serialization.SerializationException
import kotlin.time.Clock

/**
 * Token endpoint handler for the authorization-code code grant.
 */
class AuthorizationCodeTokenEndpoint(
        private val codeRepository: AuthorizationCodeRepository,
        private val tokenService: AccessTokenService,
    ) : TokenEndpointHandler {

    override fun canHandleTokenEndpointRequest(request: AccessTokenRequest): Boolean =
        request.grantTypes.contains(GrantType.AuthorizationCode.value)

    override suspend fun handleTokenEndpointRequest(request: AccessTokenRequest): AccessResponseResult {
        return try {
            if (!canHandleTokenEndpointRequest(request)) {
                return AccessResponseResult.Failure(OAuthError("unsupported_grant_type", "authorization_code grant not requested"))
            }

            val code = request.requestForm["code"]?.firstOrNull()
                ?: return AccessResponseResult.Failure(OAuthError("invalid_request", "Missing authorization code"))

            val record = codeRepository.consume(code)
                ?: return AccessResponseResult.Failure(OAuthError("invalid_grant", "Authorization code is invalid or has already been used"))

            val client = request.client
            if (client.id != record.clientId) {
                return AccessResponseResult.Failure(OAuthError("invalid_grant", "Client mismatch for authorization code"))
            }

            val redirectParam = request.requestForm["redirect_uri"]?.firstOrNull()
            // RFC6749 §4.1.3: If redirect_uri was present in the authorize request, it MUST be present here and match exactly.
            record.redirectUri?.let { authorizedRedirect ->
                if (redirectParam.isNullOrBlank() || authorizedRedirect != redirectParam) {
                    return AccessResponseResult.Failure(OAuthError("invalid_grant", "redirect_uri does not match authorization request"))
                }
            }

            val session = record.session.copy()
            val updatedRequest = request
                .withSession(session)
                .grantScopes(record.grantedScopes)
                .grantAudience(record.grantedAudience)

            // RFC6749 §5.1 and §3.3: If requested scope exceeds granted scope, reject; otherwise cap to granted.
            val requestedScope = updatedRequest.requestedScopes.ifEmpty { record.grantedScopes }
            val effectiveGrantedScope = record.grantedScopes.intersect(requestedScope)
            if (effectiveGrantedScope.isEmpty() && record.grantedScopes.isNotEmpty()) {
                return AccessResponseResult.Failure(OAuthError("invalid_scope", "Requested scopes exceed the authorized scope"))
            }
            val scopedRequest = updatedRequest.withGrantedScopes(effectiveGrantedScope)

            val expiresAt = session.expiresAt[id.walt.openid4vci.TokenType.ACCESS_TOKEN]
                ?: Clock.System.now()

            val subject = session.subject?.takeIf { it.isNotBlank() }
                ?: return AccessResponseResult.Failure(OAuthError("invalid_request", "subject is required in session"))

            val claims = defaultAccessTokenClaims(
                subject = subject,
                issuer = scopedRequest.issuerId ?: client.id,
                audience = record.grantedAudience.firstOrNull(),
                scopes = scopedRequest.grantedScopes,
                expiresAt = expiresAt,
                additional = mapOf(
                    "client_id" to client.id,
                ),
            )

            val accessToken = tokenService.createAccessToken(claims)

            return AccessResponseResult.Success(
                request = scopedRequest,
                AccessTokenResponse(
                    accessToken = accessToken,
                    tokenType = id.walt.openid4vci.core.TOKEN_TYPE_BEARER,
                    extra = emptyMap(),
                )
            )
        } catch (e: SerializationException) {
            AccessResponseResult.Failure(OAuthError("invalid_request", e.message))
        }
    }
}
