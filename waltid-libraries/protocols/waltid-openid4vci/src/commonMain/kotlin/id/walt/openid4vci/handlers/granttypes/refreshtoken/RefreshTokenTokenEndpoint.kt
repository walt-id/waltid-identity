package id.walt.openid4vci.handlers.granttypes.refreshtoken

import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.GrantType
import id.walt.openid4vci.TokenType
import id.walt.openid4vci.core.TOKEN_TYPE_BEARER
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.handlers.endpoints.token.TokenEndpointHandler
import id.walt.openid4vci.repository.authorization.DuplicateCodeException
import id.walt.openid4vci.repository.refresh.DefaultRefreshTokenRecord
import id.walt.openid4vci.repository.refresh.RefreshTokenRepository
import id.walt.openid4vci.requests.token.AccessTokenRequest
import id.walt.openid4vci.requests.token.sanitizeForStorage
import id.walt.openid4vci.responses.token.AccessTokenResponse
import id.walt.openid4vci.responses.token.AccessTokenResponseResult
import id.walt.openid4vci.tokens.access.AccessTokenIssuer
import id.walt.openid4vci.tokens.jwt.defaultAccessTokenClaims
import id.walt.openid4vci.tokens.refresh.RefreshTokenGenerationRequest
import id.walt.openid4vci.tokens.refresh.RefreshTokenIssuer
import id.walt.openid4vci.tokens.refresh.RefreshTokenVerifier
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RefreshTokenTokenEndpoint(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val accessTokenIssuer: AccessTokenIssuer,
    private val refreshTokenIssuer: RefreshTokenIssuer,
    private val refreshTokenVerifier: RefreshTokenVerifier,
) : TokenEndpointHandler {

    override fun canHandleTokenEndpointRequest(request: AccessTokenRequest): Boolean =
        request.grantTypes.contains(GrantType.RefreshToken.value)

    override suspend fun handleTokenEndpointRequest(request: AccessTokenRequest): AccessTokenResponseResult {
        if (!canHandleTokenEndpointRequest(request)) {
            return AccessTokenResponseResult.Failure(
                OAuthError("unsupported_grant_type", "refresh_token grant not requested"),
            )
        }

        return try {
            val rawRefreshToken = request.requestForm["refresh_token"]?.firstOrNull()
                ?: return AccessTokenResponseResult.Failure(OAuthError("invalid_request", "Missing refresh_token"))
            val clientId = request.client.id.takeIf { it.isNotBlank() }
                ?: return AccessTokenResponseResult.Failure(OAuthError("invalid_request", "client_id is required"))
            val verifiedRefreshToken = refreshTokenVerifier.verify(
                token = rawRefreshToken,
                expectedIssuer = request.issClaim,
                expectedClientId = clientId,
            )
            val refreshTokenSignature = refreshTokenIssuer.signature(rawRefreshToken)
            val record = refreshTokenRepository.get(refreshTokenSignature)
                ?: return AccessTokenResponseResult.Failure(OAuthError("invalid_grant", "Refresh token is invalid"))

            if (!record.active || Clock.System.now() >= record.expiresAt) {
                return AccessTokenResponseResult.Failure(OAuthError("invalid_grant", "Refresh token is invalid"))
            }

            if (clientId != record.clientId) {
                return AccessTokenResponseResult.Failure(OAuthError("invalid_grant", "Client mismatch for refresh token"))
            }

            val requestedScope = request.requestedScopes.ifEmpty { record.grantedScopes }
            if (!record.grantedScopes.containsAll(requestedScope)) {
                return AccessTokenResponseResult.Failure(OAuthError("invalid_scope", "Requested scopes exceed the authorized scope"))
            }

            val session = record.session.copy()
            val subject = session.subject?.takeIf { it.isNotBlank() }
                ?: return AccessTokenResponseResult.Failure(OAuthError("invalid_request", "subject is required in session"))

            val grantedScopes = requestedScope.toSet()
            val updatedClient = if (request.client.grantTypes.contains(GrantType.RefreshToken.value)) {
                request.client
            } else {
                DefaultClient(
                    id = clientId,
                    redirectUris = request.client.redirectUris,
                    grantTypes = request.client.grantTypes + GrantType.RefreshToken.value,
                    responseTypes = request.client.responseTypes,
                    scopes = request.client.scopes,
                    audience = request.client.audience,
                )
            }
            val updatedRequest = request
                .withClient(updatedClient)
                .withSession(session)
                .markGrantTypeHandled(GrantType.RefreshToken.value)
                .withGrantedScopes(grantedScopes)
                .withGrantedAudience(record.grantedAudience)

            val now = Clock.System.now()
            val sessionExpiresAt = session.expiresAt[TokenType.ACCESS_TOKEN]?.takeIf { it > now }
            val accessTokenExpiresAt = sessionExpiresAt ?: (now + 3600.seconds)
            val accessToken = accessTokenIssuer.issue(
                defaultAccessTokenClaims(
                    subject = subject,
                    issuer = updatedRequest.issClaim ?: clientId,
                    audience = record.grantedAudience.firstOrNull(),
                    scopes = grantedScopes,
                    issuedAt = now,
                    expiresAt = accessTokenExpiresAt,
                    additional = buildMap {
                        put("client_id", clientId)
                        session.customAttributes["issuance_session_id"]?.let {
                            put("issuance_session_id", it)
                        }
                    },
                ),
            )

            val newRefreshToken = refreshTokenIssuer.issue(
                RefreshTokenGenerationRequest(
                    issuer = updatedRequest.issClaim ?: verifiedRefreshToken.issuer,
                    subject = subject,
                    clientId = clientId,
                    scopes = record.grantedScopes,
                    expiresAt = record.expiresAt,
                    sessionId = session.customAttributes["issuance_session_id"],
                    issuedAt = now,
                )
            )
            val newRecord = DefaultRefreshTokenRecord(
                tokenSignature = refreshTokenIssuer.signature(newRefreshToken),
                requester = updatedRequest.sanitizeForStorage(),
                accessTokenSignature = accessTokenIssuer.signature(accessToken),
                clientId = clientId,
                grantedScopes = record.grantedScopes,
                grantedAudience = record.grantedAudience,
                session = session,
                expiresAt = record.expiresAt,
            )

            if (refreshTokenRepository.rotate(refreshTokenSignature, newRecord) == null) {
                return AccessTokenResponseResult.Failure(OAuthError("invalid_grant", "Refresh token is invalid"))
            }

            val expiresIn = if (sessionExpiresAt == null) 3600L else computeRemainingSeconds(accessTokenExpiresAt)
            val extra = buildMap<String, Any?> {
                put("refresh_token", newRefreshToken)
                if (grantedScopes.isNotEmpty()) {
                    put("scope", grantedScopes.joinToString(" "))
                }
            }

            AccessTokenResponseResult.Success(
                request = updatedRequest,
                response = AccessTokenResponse(
                    accessToken = accessToken,
                    tokenType = TOKEN_TYPE_BEARER,
                    expiresIn = expiresIn,
                    extra = extra,
                ),
            )
        } catch (e: DuplicateCodeException) {
            AccessTokenResponseResult.Failure(OAuthError("server_error", e.message))
        } catch (e: IllegalArgumentException) {
            AccessTokenResponseResult.Failure(OAuthError("invalid_grant", "Refresh token is invalid"))
        }
    }

    private fun computeRemainingSeconds(expiresAt: Instant): Long {
        val remaining = expiresAt - Clock.System.now()
        return if (remaining.isNegative()) 0 else remaining.inWholeSeconds
    }
}
