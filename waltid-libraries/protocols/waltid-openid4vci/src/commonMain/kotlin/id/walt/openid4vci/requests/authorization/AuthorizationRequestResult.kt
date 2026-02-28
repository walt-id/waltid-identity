package id.walt.openid4vci.requests.authorization

import id.walt.openid4vci.errors.OAuthError

sealed class AuthorizationRequestResult {
    data class Success(val request: AuthorizationRequest) : AuthorizationRequestResult()
    data class Failure(val error: OAuthError) : AuthorizationRequestResult()

    fun isSuccess(): Boolean = this is Success
}