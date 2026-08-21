package id.walt.openid4vci.handlers.granttypes.authorizationcode

import id.walt.openid4vci.DEFAULT_ACCESS_TOKEN_LIFETIME_SECONDS
import id.walt.openid4vci.DEFAULT_REFRESH_TOKEN_LIFETIME_SECONDS
import id.walt.openid4vci.GrantType
import id.walt.openid4vci.Session
import id.walt.openid4vci.TokenType
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.errors.OAuthErrorCodes
import id.walt.openid4vci.handlers.endpoints.token.TokenEndpointHandler
import id.walt.openid4vci.repository.authorization.AuthorizationCodeRepository
import id.walt.openid4vci.repository.authorization.DuplicateCodeException
import id.walt.openid4vci.repository.refresh.DefaultRefreshTokenRecord
import id.walt.openid4vci.repository.refresh.RefreshTokenRepository
import id.walt.openid4vci.requests.token.AccessTokenRequest
import id.walt.openid4vci.requests.token.sanitizeForStorage
import id.walt.openid4vci.responses.token.AccessTokenResponse
import id.walt.openid4vci.responses.token.AccessTokenResponseResult
import id.walt.openid4vci.tokens.access.AccessTokenIssuer
import id.walt.openid4vci.tokens.access.accessTokenType
import id.walt.openid4vci.tokens.access.dpopAccessTokenClaims
import id.walt.openid4vci.tokens.refresh.RefreshTokenGenerationRequest
import id.walt.openid4vci.tokens.refresh.RefreshTokenIssuer
import id.walt.openid4vci.tokens.jwt.defaultAccessTokenClaims
import kotlinx.serialization.SerializationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Token endpoint handler for the authorization-code code grant.
 */
class AuthorizationCodeTokenEndpoint(
    private val codeRepository: AuthorizationCodeRepository,
    private val accessTokenIssuer: AccessTokenIssuer,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val refreshTokenIssuer: RefreshTokenIssuer,
    private val refreshTokenLifetimeSeconds: Long = DEFAULT_REFRESH_TOKEN_LIFETIME_SECONDS,
) : TokenEndpointHandler {

    override fun canHandleTokenEndpointRequest(request: AccessTokenRequest): Boolean =
        request.grantTypes.contains(GrantType.AuthorizationCode.value)

    override suspend fun handleTokenEndpointRequest(request: AccessTokenRequest): AccessTokenResponseResult {
        val unresolvedRequest = request.withSession(null)

        if (!canHandleTokenEndpointRequest(request)) {
            return AccessTokenResponseResult.Failure(
                unresolvedRequest,
                OAuthError(OAuthErrorCodes.UNSUPPORTED_GRANT_TYPE, "authorization_code grant not requested"),
            )
        }

        val code = request.requestForm["code"]?.firstOrNull()
            ?: return AccessTokenResponseResult.Failure(
                unresolvedRequest,
                OAuthError(OAuthErrorCodes.INVALID_REQUEST, "Missing authorization code"),
            )

        val record = try {
            codeRepository.consume(code)
        } catch (e: SerializationException) {
            return AccessTokenResponseResult.Failure(
                unresolvedRequest,
                OAuthError(OAuthErrorCodes.INVALID_REQUEST, e.message)
            )
        } catch (e: DuplicateCodeException) {
            return AccessTokenResponseResult.Failure(
                unresolvedRequest,
                OAuthError(OAuthErrorCodes.SERVER_ERROR, e.message)
            )
        } ?: return AccessTokenResponseResult.Failure(
            unresolvedRequest,
            OAuthError(OAuthErrorCodes.INVALID_GRANT, "Authorization code is invalid or has already been used"),
        )

        val resolvedRequest = unresolvedRequest.withSession(record.session.copy())

        return try {
            val session = requireNotNull(resolvedRequest.session)
            val client = request.client
            if (client.id != record.clientId) {
                return AccessTokenResponseResult.Failure(
                    resolvedRequest,
                    OAuthError(OAuthErrorCodes.INVALID_GRANT, "Client mismatch for authorization code"),
                )
            }

            val redirectParam = request.requestForm["redirect_uri"]?.firstOrNull()
            // RFC6749 §4.1.3: If redirect_uri was present in the authorize request, it MUST be present here and match exactly.
            record.redirectUri?.let { authorizedRedirect ->
                if (redirectParam.isNullOrBlank() || authorizedRedirect != redirectParam) {
                    return AccessTokenResponseResult.Failure(
                        resolvedRequest,
                        OAuthError(OAuthErrorCodes.INVALID_GRANT, "redirect_uri does not match authorization request"),
                    )
                }
            }

            val updatedRequest = resolvedRequest
                .grantScopes(record.grantedScopes)
                .grantAudience(record.grantedAudience)

            // RFC6749 §5.1 and §3.3: If requested scope exceeds granted scope, reject; otherwise cap to granted.
            val requestedScope = updatedRequest.requestedScopes.ifEmpty { record.grantedScopes }
            if (!record.grantedScopes.containsAll(requestedScope)) {
                return AccessTokenResponseResult.Failure(
                    resolvedRequest,
                    OAuthError(OAuthErrorCodes.INVALID_SCOPE, "Requested scopes exceed the authorized scope"),
                )
            }
            val scopedRequest = updatedRequest.withGrantedScopes(requestedScope.toSet())

            val sessionExpiresAt = session.expiresAt[TokenType.ACCESS_TOKEN]
            val expiresAt = sessionExpiresAt ?: (Clock.System.now() + DEFAULT_ACCESS_TOKEN_LIFETIME_SECONDS.seconds)

            val subject = session.subject?.takeIf { it.isNotBlank() }
                ?: return AccessTokenResponseResult.Failure(
                    resolvedRequest,
                    OAuthError(OAuthErrorCodes.INVALID_REQUEST, "subject is required in session"),
                )

            val claims = defaultAccessTokenClaims(
                subject = subject,
                issuer = scopedRequest.issClaim ?: client.id,
                audience = record.grantedAudience.firstOrNull(),
                scopes = scopedRequest.grantedScopes,
                expiresAt = expiresAt,
                additional = buildMap {
                    put("client_id", client.id)
                    session.customAttributes["issuance_session_id"]?.let {
                        put("issuance_session_id", it)
                    }
                    putAll(scopedRequest.dpopAccessTokenClaims())
                },
            )

            val accessToken = accessTokenIssuer.issue(claims)

            val refreshToken = issueRefreshToken(
                request = scopedRequest,
                accessToken = accessToken,
                session = session,
                subject = subject,
                issuer = scopedRequest.issClaim ?: client.id,
                clientId = client.id,
                grantedScopes = scopedRequest.grantedScopes,
                grantedAudience = record.grantedAudience,
            )

            // If no explicit session expiry is configured, return the default lifetime exactly.
            // If an explicit expiry exists, return the real remaining lifetime from now.
            val expiresIn = if (sessionExpiresAt == null) {
                DEFAULT_ACCESS_TOKEN_LIFETIME_SECONDS
            } else {
                computeRemainingSeconds(expiresAt)
            }

            return AccessTokenResponseResult.Success(
                request = scopedRequest,
                AccessTokenResponse(
                    accessToken = accessToken,
                    tokenType = scopedRequest.accessTokenType(),
                    expiresIn = expiresIn,
                    refreshToken = refreshToken,
                ),
            )
        } catch (e: SerializationException) {
            AccessTokenResponseResult.Failure(resolvedRequest, OAuthError(OAuthErrorCodes.INVALID_REQUEST, e.message))
        } catch (e: DuplicateCodeException) {
            AccessTokenResponseResult.Failure(resolvedRequest, OAuthError(OAuthErrorCodes.SERVER_ERROR, e.message))
        }
    }

    private suspend fun issueRefreshToken(
        request: AccessTokenRequest,
        accessToken: String,
        session: Session,
        subject: String,
        issuer: String,
        clientId: String,
        grantedScopes: Set<String>,
        grantedAudience: Set<String>,
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
                sessionId = refreshSession.customAttributes["issuance_session_id"],
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
