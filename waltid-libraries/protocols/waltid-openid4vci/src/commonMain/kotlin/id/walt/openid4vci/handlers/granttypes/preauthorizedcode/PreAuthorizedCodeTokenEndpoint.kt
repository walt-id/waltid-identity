package id.walt.openid4vci.handlers.granttypes.preauthorizedcode

import id.walt.openid4vci.DEFAULT_ACCESS_TOKEN_LIFETIME_SECONDS
import id.walt.openid4vci.DEFAULT_REFRESH_TOKEN_LIFETIME_SECONDS
import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.GrantType
import id.walt.openid4vci.Session
import id.walt.openid4vci.TokenType
import id.walt.openid4vci.core.TOKEN_TYPE_BEARER
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.handlers.endpoints.token.TokenEndpointHandler
import id.walt.openid4vci.preauthorized.hashTxCode
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRepository
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
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Token endpoint handler for the OpenID4VCI pre-authorized code grant.
 *
 * Consumes a stored pre-authorized code session. The business logic will be modified, is just the skeleton that is working.
 */
class PreAuthorizedCodeTokenEndpoint(
    private val codeRepository: PreAuthorizedCodeRepository,
    private val accessTokenIssuer: AccessTokenIssuer,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val refreshTokenIssuer: RefreshTokenIssuer,
    private val refreshTokenLifetimeSeconds: Long = DEFAULT_REFRESH_TOKEN_LIFETIME_SECONDS,
) : TokenEndpointHandler {

    override fun canHandleTokenEndpointRequest(request: AccessTokenRequest): Boolean =
        request.grantTypes.contains(GrantType.PreAuthorizedCode.value)

    override suspend fun handleTokenEndpointRequest(request: AccessTokenRequest): AccessTokenResponseResult {
        if (!canHandleTokenEndpointRequest(request)) {
            return AccessTokenResponseResult.Failure(
                OAuthError(
                    error = "unsupported_grant_type",
                    description = "${GrantType.PreAuthorizedCode.value} grant not requested",
                ),
            )
        }

        val code = request.requestForm["pre-authorized_code"]?.firstOrNull()
            ?: return AccessTokenResponseResult.Failure(OAuthError("invalid_request", "Missing pre-authorized_code"))

        val record = codeRepository.get(code)
            ?: return AccessTokenResponseResult.Failure(
                OAuthError(
                    error = "invalid_grant",
                    description = "Pre-authorized code is invalid or has already been used",
                ),
            )

        record.clientId?.let { boundClientId ->
            val authenticatedClient = request.authenticatedClient
                ?: return AccessTokenResponseResult.Failure(
                    OAuthError("invalid_client", "Client authentication is required for this pre-authorized code"),
                )

            if (authenticatedClient.id != boundClientId) {
                return AccessTokenResponseResult.Failure(
                    OAuthError("invalid_grant", "Client mismatch for pre-authorized code"),
                )
            }
        }

        val providedTxCode = request.requestForm["tx_code"]?.firstOrNull()
        if (record.txCode != null && providedTxCode.isNullOrBlank()) {
            return AccessTokenResponseResult.Failure(
                OAuthError(
                    error = "invalid_grant",
                    description = "tx_code is required for this pre-authorized code",
                ),
            )
        }

        if (!record.txCodeValue.isNullOrBlank() &&
            providedTxCode != null &&
            record.txCodeValue != hashTxCode(providedTxCode)
        ) {
            return AccessTokenResponseResult.Failure(
                OAuthError(
                    error = "invalid_grant",
                    description = "tx_code is invalid",
                ),
            )
        }

        val consumed = codeRepository.consume(code)
            ?: return AccessTokenResponseResult.Failure(
                OAuthError(
                    error = "invalid_grant",
                    description = "Pre-authorized code is invalid or has already been used",
                ),
            )

        val session = consumed.session.copy()
        val updatedRequest = request
            .withSession(session)
            .markGrantTypeHandled(GrantType.PreAuthorizedCode.value)
            .grantScopes(consumed.grantedScopes)
            .grantAudience(consumed.grantedAudience)

        val existingClient = updatedRequest.client
        val clientId = consumed.clientId ?: existingClient.id
        val clientToUse = if (existingClient.id != clientId) {
            DefaultClient(
                id = clientId,
                redirectUris = emptyList(),
                grantTypes = setOf(GrantType.PreAuthorizedCode.value),
                responseTypes = emptySet(),
            )
        } else {
            existingClient
        }
        val clientRequest = updatedRequest.withClient(clientToUse)

        val sessionExpiresAt = session.expiresAt[TokenType.ACCESS_TOKEN]
        val expiresAt = sessionExpiresAt ?: (Clock.System.now() + DEFAULT_ACCESS_TOKEN_LIFETIME_SECONDS.seconds)

        val subject = session.subject?.takeIf { it.isNotBlank() }
            ?: return AccessTokenResponseResult.Failure(OAuthError("invalid_request", "subject is required in session"))

        val claims = defaultAccessTokenClaims(
            subject = subject,
            issuer = clientRequest.issClaim ?: clientId,
            audience = consumed.grantedAudience.firstOrNull(),
            scopes = clientRequest.grantedScopes,
            expiresAt = expiresAt,
            additional = buildMap {
                clientId.takeIf { it.isNotBlank() }?.let { put("client_id", it) }
                put("pre_authorized_code", code)
                consumed.issuanceSessionId?.let { put("issuance_session_id", it) }
            },
        )

        val accessToken = accessTokenIssuer.issue(claims)
        val refreshIssuer = clientRequest.issClaim ?: clientId.takeIf { it.isNotBlank() }
        val refreshToken = refreshIssuer?.let { issuer ->
            issueRefreshToken(
                request = clientRequest,
                accessToken = accessToken,
                session = session,
                subject = subject,
                issuer = issuer,
                clientId = clientId.takeIf { it.isNotBlank() },
                grantedScopes = clientRequest.grantedScopes,
                grantedAudience = consumed.grantedAudience,
                sessionId = consumed.issuanceSessionId,
            )
        }

        val scope = clientRequest.grantedScopes.takeIf { it.isNotEmpty() }?.joinToString(" ")
        val extra = buildMap<String, Any?> {
            consumed.credentialNonce?.let { put("c_nonce", it) }
            consumed.credentialNonceExpiresAt?.let { expiresAt ->
                put("c_nonce_expires_in", computeRemainingSeconds(expiresAt))
            }
        }

        // If no explicit session expiry is configured, return the default lifetime exactly.
        // If an explicit expiry exists, return the real remaining lifetime from now.
        val expiresIn = if (sessionExpiresAt == null) {
            DEFAULT_ACCESS_TOKEN_LIFETIME_SECONDS
        } else {
            computeRemainingSeconds(expiresAt)
        }

        return AccessTokenResponseResult.Success(
            request = clientRequest,
            AccessTokenResponse(
                accessToken = accessToken,
                tokenType = TOKEN_TYPE_BEARER,
                expiresIn = expiresIn,
                refreshToken = refreshToken,
                scope = scope,
                extra = extra,
            ),
        )
    }

    private suspend fun issueRefreshToken(
        request: AccessTokenRequest,
        accessToken: String,
        session: Session,
        subject: String,
        issuer: String,
        clientId: String?,
        grantedScopes: Set<String>,
        grantedAudience: Set<String>,
        sessionId: String?,
    ): String {
        val refreshTokenExpiresAt = session.expiresAt[TokenType.REFRESH_TOKEN]
            ?: (Clock.System.now() + refreshTokenLifetimeSeconds.seconds)
        val refreshSession = session.copy().withExpiresAt(TokenType.REFRESH_TOKEN, refreshTokenExpiresAt)
        val refreshToken = refreshTokenIssuer.issue(
            RefreshTokenGenerationRequest(
                issuer = issuer,
                subject = subject,
                clientId = clientId,
                scopes = grantedScopes,
                expiresAt = refreshTokenExpiresAt,
                sessionId = sessionId ?: refreshSession.customAttributes["issuance_session_id"],
            )
        )

        refreshTokenRepository.save(
            DefaultRefreshTokenRecord(
                tokenSignature = refreshTokenIssuer.signature(refreshToken),
                accessTokenRequest = request.sanitizeForStorage(),
                accessTokenSignature = accessTokenIssuer.signature(accessToken),
                clientId = clientId,
                grantedScopes = grantedScopes,
                grantedAudience = grantedAudience,
                session = refreshSession,
                expiresAt = refreshTokenExpiresAt,
            ),
        )

        return refreshToken
    }

    private fun computeRemainingSeconds(expiresAt: Instant): Long {
        val remaining = expiresAt - Clock.System.now()
        return if (remaining.isNegative()) 0 else remaining.inWholeSeconds
    }
}
