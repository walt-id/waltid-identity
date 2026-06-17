package id.walt.openid4vci.handlers.endpoints.par

import id.walt.crypto.utils.UuidUtils
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.errors.OAuthErrorCodes
import id.walt.openid4vci.repository.par.PAREntry
import id.walt.openid4vci.repository.par.PARRepository
import id.walt.openid4vci.requests.authorization.AuthorizationRequest
import id.walt.openid4vci.responses.par.PushedAuthorizationResponse
import id.walt.openid4vci.responses.par.PushedAuthorizationResponseResult
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class DefaultPushedAuthorizationEndpoint(
    private val parRepository: PARRepository,
    private val requestUriPrefix: String = PushedAuthorizationResponse.DEFAULT_REQUEST_URI_PREFIX,
    private val requestLifetimeSeconds: Int = 90,
    private val requestIdGenerator: () -> String = { UuidUtils.randomUUIDString() },
) : PushedAuthorizationEndpointHandler {

    init {
        require(requestUriPrefix.isNotBlank()) { "requestUriPrefix must not be blank" }
        require(requestLifetimeSeconds > 0) { "PAR expiry must be positive" }
    }

    override suspend fun handlePushedAuthorizationEndpointRequest(
        authorizationRequest: AuthorizationRequest,
        clientAuthentication: Map<String, String>,
    ): PushedAuthorizationResponseResult {
        return try {
            val requestId = requestIdGenerator()
            val now = Clock.System.now()
            val entry = PAREntry(
                requestId = requestId,
                requestParameters = authorizationRequest.requestForm.sanitizedAuthorizationParameters(),
                createdAt = now,
                expiresAt = now + requestLifetimeSeconds.seconds,
                clientMetadata = clientAuthentication,
            )
            parRepository.store(entry)
            PushedAuthorizationResponseResult.Success(
                request = authorizationRequest,
                response = PushedAuthorizationResponse.create(
                    requestId = requestId,
                    expiresIn = requestLifetimeSeconds,
                    requestUriPrefix = requestUriPrefix,
                ),
            )
        } catch (e: IllegalArgumentException) {
            PushedAuthorizationResponseResult.Failure(
                OAuthError(OAuthErrorCodes.INVALID_REQUEST, e.message),
            )
        }
    }

    private fun Map<String, List<String>>.sanitizedAuthorizationParameters(): Map<String, List<String>> =
        filterKeys { it !in endpointOnlyParameters }

    private companion object {
        val endpointOnlyParameters = setOf(
            "client_secret",
            "client_assertion",
            "client_assertion_type",
        )
    }
}
