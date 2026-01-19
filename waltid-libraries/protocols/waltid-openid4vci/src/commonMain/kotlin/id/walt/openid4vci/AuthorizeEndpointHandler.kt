package id.walt.openid4vci

import id.walt.openid4vci.core.AuthorizeResponseResult
import id.walt.openid4vci.request.AuthorizationRequest

/**
 * Simplified authorize endpoint handler contract shared across provider implementations.
 *
 * Handlers should only return failure or something similar when the flow must abort.
 * When a handler does not apply to the incoming request it should let the dispatcher offer the request to the next handler.
 */
interface AuthorizeEndpointHandler {
    suspend fun handleAuthorizeEndpointRequest(request: AuthorizationRequest, session: Session): AuthorizeResponseResult
}
