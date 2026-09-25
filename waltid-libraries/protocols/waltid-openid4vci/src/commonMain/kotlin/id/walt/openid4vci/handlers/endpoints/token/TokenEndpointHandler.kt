package id.walt.openid4vci.handlers.endpoints.token

import id.walt.openid4vci.requests.token.AccessTokenRequest
import id.walt.openid4vci.responses.token.AccessTokenResponseResult
import id.walt.openid4vci.responses.token.TokenResponseOptions

/**
 * Token endpoint handler interface.
 */
interface TokenEndpointHandler {
    fun canHandleTokenEndpointRequest(request: AccessTokenRequest): Boolean
    suspend fun handleTokenEndpointRequest(request: AccessTokenRequest, options: TokenResponseOptions = TokenResponseOptions()): AccessTokenResponseResult
}
