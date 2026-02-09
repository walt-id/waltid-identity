package id.walt.openid4vci

import id.walt.openid4vci.request.AccessTokenRequest

/**
 * Token endpoint handler contract used by the provider.
 *
 * Each handler decides if it can process the incoming token request and, if so,
 * returns either tokens or an OAuth error packaged
 */
interface TokenEndpointHandler {
    fun canHandleTokenEndpointRequest(request: AccessTokenRequest): Boolean
    suspend fun handleTokenEndpointRequest(request: AccessTokenRequest): TokenEndpointResult
}
