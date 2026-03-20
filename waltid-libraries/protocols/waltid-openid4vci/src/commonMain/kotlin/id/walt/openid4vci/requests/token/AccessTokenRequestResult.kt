package id.walt.openid4vci.requests.token

import id.walt.openid4vci.errors.OAuthError

sealed class AccessTokenRequestResult {
    data class Success(val request: AccessTokenRequest) : AccessTokenRequestResult()
    data class Failure(val error: OAuthError) : AccessTokenRequestResult()

    fun isSuccess(): Boolean = this is Success
}
