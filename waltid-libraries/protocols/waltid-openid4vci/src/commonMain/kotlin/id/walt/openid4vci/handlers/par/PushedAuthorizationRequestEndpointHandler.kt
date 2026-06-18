package id.walt.openid4vci.handlers.par

import id.walt.crypto.utils.UuidUtils
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.errors.OAuthErrorCodes
import id.walt.openid4vci.handlers.endpoints.par.PushedAuthorizationEndpointHandler
import id.walt.openid4vci.repository.par.DefaultPARRecord
import id.walt.openid4vci.repository.par.DuplicatePARRecordException
import id.walt.openid4vci.repository.par.PARRepository
import id.walt.openid4vci.requests.authorization.AuthorizationRequest
import id.walt.openid4vci.responses.par.PushedAuthorizationResponse
import id.walt.openid4vci.responses.par.PushedAuthorizationResponseResult
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class PushedAuthorizationRequestEndpointHandler(
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
        val requestId = requestIdGenerator()
        val now = Clock.System.now()
        val record = try {
            DefaultPARRecord(
                requestId = requestId,
                requestParameters = authorizationRequest.requestForm.withoutEndpointOnlyParameters(),
                createdAt = now,
                expiresAt = now + requestLifetimeSeconds.seconds,
                clientMetadata = clientAuthentication,
            )
        } catch (e: IllegalArgumentException) {
            return PushedAuthorizationResponseResult.Failure(
                OAuthError(OAuthErrorCodes.INVALID_REQUEST, e.message),
            )
        }

        return try {
            parRepository.save(record)
            PushedAuthorizationResponseResult.Success(
                request = authorizationRequest,
                response = PushedAuthorizationResponse.create(
                    requestId = requestId,
                    expiresIn = requestLifetimeSeconds,
                    requestUriPrefix = requestUriPrefix,
                ),
            )
        } catch (e: DuplicatePARRecordException) {
            PushedAuthorizationResponseResult.Failure(
                OAuthError(OAuthErrorCodes.SERVER_ERROR, "Unable to store pushed authorization request"),
            )
        }
    }

    private fun Map<String, List<String>>.withoutEndpointOnlyParameters(): Map<String, List<String>> =
        filterKeys { it !in endpointOnlyParameters }

    private companion object {
        val endpointOnlyParameters = setOf(
            "client_secret",
            "client_assertion",
            "client_assertion_type",
        )
    }
}