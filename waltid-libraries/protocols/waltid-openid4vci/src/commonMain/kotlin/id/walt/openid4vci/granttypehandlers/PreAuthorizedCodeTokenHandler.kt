package id.walt.openid4vci.granttypehandlers

import id.walt.openid4vci.GRANT_TYPE_PRE_AUTHORIZED_CODE
import id.walt.openid4vci.TokenEndpointHandler
import id.walt.openid4vci.TokenEndpointResult
import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.argumentsOf
import id.walt.openid4vci.newArguments
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRepository
import id.walt.openid4vci.request.AccessTokenRequest
import id.walt.openid4vci.tokens.TokenService
import kotlin.time.Instant
import kotlin.time.ExperimentalTime

/**
 * Token endpoint handler for the OpenID4VCI pre-authorized code grant.
 *
 * Consumes a stored pre-authorized code session. The business logic will be modified, is just the skeleton that is working.
 */
class PreAuthorizedCodeTokenHandler(
    private val codeRepository: PreAuthorizedCodeRepository,
    private val tokenService: TokenService = TokenService(),
) : TokenEndpointHandler {

    override fun canHandleTokenEndpointRequest(request: AccessTokenRequest): Boolean =
        request.getGrantTypes().contains(GRANT_TYPE_PRE_AUTHORIZED_CODE)

    @OptIn(ExperimentalTime::class)
    override fun handleTokenEndpointRequest(request: AccessTokenRequest): TokenEndpointResult {
        if (!canHandleTokenEndpointRequest(request)) {
            return TokenEndpointResult.Failure(
                error = "unsupported_grant_type",
                description = "$GRANT_TYPE_PRE_AUTHORIZED_CODE grant not requested",
            )
        }

        val code = request.getRequestForm().getFirst("pre-authorized_code")
            ?: return TokenEndpointResult.Failure("invalid_request", "Missing pre-authorized_code")

        val issuerId = request.getIssuerId()
            ?: return TokenEndpointResult.Failure("invalid_request", "Issuer context missing")

        val record = codeRepository.get(code, issuerId)
            ?: return TokenEndpointResult.Failure(
                "invalid_grant",
                "Pre-authorized code is invalid or has already been used",
            )

        val providedPin = request.getRequestForm().getFirst("user_pin")
        if (record.userPinRequired && providedPin.isNullOrBlank()) {
            return TokenEndpointResult.Failure(
                "invalid_grant",
                "user_pin is required for this pre-authorized code",
            )
        }

        if (!record.userPin.isNullOrBlank() && record.userPin != providedPin) {
            return TokenEndpointResult.Failure(
                "invalid_grant",
                "user_pin is invalid",
            )
        }

        val consumed = codeRepository.consume(code, issuerId)
            ?: return TokenEndpointResult.Failure(
                "invalid_grant",
                "Pre-authorized code is invalid or has already been used",
            )

        val session = consumed.session.cloneSession()
        request.setSession(session)
        request.markGrantTypeHandled(GRANT_TYPE_PRE_AUTHORIZED_CODE)

        consumed.grantedScopes.forEach(request::grantScope)
        consumed.grantedAudience.forEach(request::grantAudience)

        val existingClient = request.getClient()
        val clientId = consumed.clientId ?: existingClient.id
        if (existingClient.id != clientId) {
            request.setClient(
                DefaultClient(
                    id = clientId,
                    grantTypes = argumentsOf(GRANT_TYPE_PRE_AUTHORIZED_CODE),
                    responseTypes = newArguments(),
                ),
            )
        }

        val accessToken = tokenService.createAccessToken(clientId, code)

        val extra = buildMap<String, Any?> {
            consumed.credentialNonce?.let { put("c_nonce", it) }
            consumed.credentialNonceExpiresAt?.let { expiresAt ->
                put("c_nonce_expires_in", computeRemainingSeconds(expiresAt))
            }
        }

        return TokenEndpointResult.Success(
            accessToken = accessToken,
            extra = extra,
        )
    }

    @OptIn(ExperimentalTime::class)
    private fun computeRemainingSeconds(expiresAt: Instant): Long {
        val remaining = expiresAt - kotlin.time.Clock.System.now()
        return if (remaining.isNegative()) 0 else remaining.inWholeSeconds
    }
}
