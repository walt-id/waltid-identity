package id.walt.openid4vci.responses.authorization

import id.walt.openid4vci.ResponseMode

data class AuthorizationResponse(
    val redirectUri: String,
    val code: String,
    val state: String? = null,
    val scope: String? = null,
    val responseMode: ResponseMode = ResponseMode.QUERY,
    val extraParameters: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
)

sealed class AuthorizationResponseResult {
    data class Success(val response: AuthorizationResponse) : AuthorizationResponseResult()
    data class Failure(val error: id.walt.openid4vci.errors.OAuthError) : AuthorizationResponseResult()

    fun isSuccess(): Boolean = this is Success
}

data class AuthorizationResponseHttp(
    val status: Int,
    val redirectUri: String?,
    val parameters: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
)