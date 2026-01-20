package id.walt.openid4vci.requests.authorization

import id.walt.openid4vci.errors.OAuthError

sealed class AuthorizeRequestResult {
    data class Success(val request: AuthorizationRequest) : AuthorizeRequestResult()
    data class Failure(val error: OAuthError) : AuthorizeRequestResult()

    fun isSuccess(): Boolean = this is Success
}