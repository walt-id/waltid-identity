package id.walt.openid4vci.handlers.authorization

import id.walt.openid4vci.Session
import id.walt.openid4vci.requests.authorization.AuthorizationRequest
import id.walt.openid4vci.responses.authorization.AuthorizeResponseResult

/**
 * Simplified authorize endpoint handler contract shared across provider implementations.
 *
 * Handlers should only return failure or something similar when the flow must abort.
 * When a handler does not apply to the incoming request it should let the dispatcher offer the request to the next handler.
 */
interface AuthorizationEndpointHandler {
    suspend fun handleAuthorizeEndpointRequest(
        authorizationRequest: AuthorizationRequest,
        session: Session,
    ): AuthorizeResponseResult
}
