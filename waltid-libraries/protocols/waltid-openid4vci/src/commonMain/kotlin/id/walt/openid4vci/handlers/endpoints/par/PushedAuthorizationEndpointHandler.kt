package id.walt.openid4vci.handlers.endpoints.par

import id.walt.openid4vci.requests.authorization.AuthorizationRequest
import id.walt.openid4vci.responses.par.PushedAuthorizationResponseResult

/**
 * PAR endpoint handler contract.
 */
interface PushedAuthorizationEndpointHandler {
    suspend fun handlePushedAuthorizationEndpointRequest(
        authorizationRequest: AuthorizationRequest,
        clientAuthentication: Map<String, String> = emptyMap(),
    ): PushedAuthorizationResponseResult
}