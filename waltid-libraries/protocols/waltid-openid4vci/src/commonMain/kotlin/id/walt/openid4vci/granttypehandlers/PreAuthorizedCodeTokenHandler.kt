package id.walt.openid4vci.granttypehandlers

import id.walt.openid4vci.GrantType
import id.walt.openid4vci.TokenEndpointHandler
import id.walt.openid4vci.TokenEndpointResult
import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.argumentsOf
import id.walt.openid4vci.newArguments
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRepository
import id.walt.openid4vci.request.AccessTokenRequest
import id.walt.openid4vci.tokens.AccessTokenService
import id.walt.openid4vci.tokens.jwt.defaultAccessTokenClaims
import id.walt.openid4vci.preauthorized.hashPin
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Token endpoint handler for the OpenID4VCI pre-authorized code grant.
 *
 * Consumes a stored pre-authorized code session. The business logic will be modified, is just the skeleton that is working.
 */
    class PreAuthorizedCodeTokenHandler(
        private val codeRepository: PreAuthorizedCodeRepository,
        private val tokenService: AccessTokenService,
    ) : TokenEndpointHandler {

    override fun canHandleTokenEndpointRequest(request: AccessTokenRequest): Boolean =
        request.getGrantTypes().contains(GrantType.PreAuthorizedCode.value)

    override suspend fun handleTokenEndpointRequest(request: AccessTokenRequest): TokenEndpointResult {
        if (!canHandleTokenEndpointRequest(request)) {
            return TokenEndpointResult.Failure(
                error = "unsupported_grant_type",
                description = "${GrantType.PreAuthorizedCode.value} grant not requested",
            )
        }

        val code = request.getRequestForm().getFirst("pre-authorized_code")
            ?: return TokenEndpointResult.Failure("invalid_request", "Missing pre-authorized_code")

        val record = codeRepository.get(code)
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

        if (!record.userPin.isNullOrBlank() && providedPin != null && record.userPin != hashPin(providedPin)) {
            return TokenEndpointResult.Failure(
                "invalid_grant",
                "user_pin is invalid",
            )
        }

        val consumed = codeRepository.consume(code)
            ?: return TokenEndpointResult.Failure(
                "invalid_grant",
                "Pre-authorized code is invalid or has already been used",
            )

        val session = consumed.session.cloneSession()
        request.setSession(session)
        request.markGrantTypeHandled(GrantType.PreAuthorizedCode.value)

        consumed.grantedScopes.forEach(request::grantScope)
        consumed.grantedAudience.forEach(request::grantAudience)

        val existingClient = request.getClient()
        val clientId = consumed.clientId ?: existingClient.id
        if (existingClient.id != clientId) {
            request.setClient(
                DefaultClient(
                    id = clientId,
                    grantTypes = argumentsOf(GrantType.PreAuthorizedCode.value),
                    responseTypes = newArguments(),
                ),
            )
        }

        val expiresAt = request.getSession()?.getExpiresAt(id.walt.openid4vci.TokenType.ACCESS_TOKEN)
            ?: Clock.System.now()

        val subject = request.getSession()?.getSubject()?.takeIf { it.isNotBlank() }
            ?: return TokenEndpointResult.Failure("invalid_request", "subject is required in session")

        val claims = defaultAccessTokenClaims(
            subject = subject,
            issuer = request.getIssuerId() ?: clientId,
            audience = request.getGrantedAudience().toSet().firstOrNull(),
            scopes = request.getGrantedScopes().toSet(),
            expiresAt = expiresAt,
            additional = buildMap {
                put("client_id", clientId)
                put("pre_authorized_code", code)
            },
        )

        val accessToken = tokenService.createAccessToken(claims)

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

    private fun computeRemainingSeconds(expiresAt: Instant): Long {
        val remaining = expiresAt - Clock.System.now()
        return if (remaining.isNegative()) 0 else remaining.inWholeSeconds
    }
}
