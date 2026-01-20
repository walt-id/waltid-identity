package id.walt.openid4vci.handlers.token

import id.walt.openid4vci.requests.token.AccessTokenRequest
import id.walt.openid4vci.responses.token.AccessResponseResult

/**
 * Token endpoint handler interface.
 */
interface TokenEndpointHandler {
    fun canHandleTokenEndpointRequest(request: AccessTokenRequest): Boolean
    suspend fun handleTokenEndpointRequest(request: AccessTokenRequest): AccessResponseResult
}
