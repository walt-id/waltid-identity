package id.walt.openid4vci.handlers.granttypes.preauthorizedcode

import id.walt.openid4vci.GrantType
import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.handlers.token.TokenEndpointHandler
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.responses.token.AccessResponseResult
import id.walt.openid4vci.responses.token.AccessTokenResponse
import id.walt.openid4vci.preauthorized.hashPin
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRepository
import id.walt.openid4vci.requests.token.AccessTokenRequest
import id.walt.openid4vci.tokens.AccessTokenService
import id.walt.openid4vci.tokens.jwt.defaultAccessTokenClaims
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Token endpoint handler for the OpenID4VCI pre-authorized code grant.
 *
 * Consumes a stored pre-authorized code session. The business logic will be modified, is just the skeleton that is working.
 */
class PreAuthorizedCodeTokenEndpoint(
    private val codeRepository: PreAuthorizedCodeRepository,
    private val tokenService: AccessTokenService,
) : TokenEndpointHandler {

    override fun canHandleTokenEndpointRequest(request: AccessTokenRequest): Boolean =
        request.grantTypes.contains(GrantType.PreAuthorizedCode.value)

    override suspend fun handleTokenEndpointRequest(request: AccessTokenRequest): AccessResponseResult {
        if (!canHandleTokenEndpointRequest(request)) {
            return AccessResponseResult.Failure(
                OAuthError(
                    error = "unsupported_grant_type",
                    description = "${GrantType.PreAuthorizedCode.value} grant not requested",
                ),
            )
        }

        val code = request.requestForm["pre-authorized_code"]?.firstOrNull()
            ?: return AccessResponseResult.Failure(OAuthError("invalid_request", "Missing pre-authorized_code"))

        val record = codeRepository.get(code)
            ?: return AccessResponseResult.Failure(
                OAuthError(
                    error = "invalid_grant",
                    description = "Pre-authorized code is invalid or has already been used",
                ),
            )

        val providedPin = request.requestForm["user_pin"]?.firstOrNull()
        if (record.userPinRequired && providedPin.isNullOrBlank()) {
            return AccessResponseResult.Failure(
                OAuthError(
                    error = "invalid_grant",
                    description = "user_pin is required for this pre-authorized code",
                ),
            )
        }

        if (!record.userPin.isNullOrBlank() && providedPin != null && record.userPin != hashPin(providedPin)) {
            return AccessResponseResult.Failure(
                OAuthError(
                    error = "invalid_grant",
                    description = "user_pin is invalid",
                ),
            )
        }

        val consumed = codeRepository.consume(code)
            ?: return AccessResponseResult.Failure(
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

        val expiresAt = session.expiresAt[id.walt.openid4vci.TokenType.ACCESS_TOKEN]
            ?: Clock.System.now()

        val subject = session.subject?.takeIf { it.isNotBlank() }
            ?: return AccessResponseResult.Failure(OAuthError("invalid_request", "subject is required in session"))

        val claims = defaultAccessTokenClaims(
            subject = subject,
            issuer = clientRequest.issuerId ?: clientId,
            audience = consumed.grantedAudience.firstOrNull(),
            scopes = clientRequest.grantedScopes,
            expiresAt = expiresAt,
            additional = buildMap {
                put("client_id", clientId)
                put("pre_authorized_code", code)
            },
        )

        val accessToken = tokenService.createAccessToken(claims)

        val extra = buildMap<String, Any?> {
            if (clientRequest.grantedScopes.isNotEmpty()) {
                put("scope", clientRequest.grantedScopes.joinToString(" "))
            }
            consumed.credentialNonce?.let { put("c_nonce", it) }
            consumed.credentialNonceExpiresAt?.let { expiresAt ->
                put("c_nonce_expires_in", computeRemainingSeconds(expiresAt))
            }
        }

        return AccessResponseResult.Success(
            request = clientRequest,
            AccessTokenResponse(
                accessToken = accessToken,
                tokenType = id.walt.openid4vci.core.TOKEN_TYPE_BEARER,
                extra = extra,
            ),
        )
    }

    private fun computeRemainingSeconds(expiresAt: Instant): Long {
        val remaining = expiresAt - Clock.System.now()
        return if (remaining.isNegative()) 0 else remaining.inWholeSeconds
    }
}
