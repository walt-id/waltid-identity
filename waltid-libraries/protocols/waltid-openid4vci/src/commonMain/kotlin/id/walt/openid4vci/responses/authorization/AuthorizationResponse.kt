package id.walt.openid4vci.responses.authorization

import id.walt.openid4vci.ResponseModeType

data class AuthorizeResponse(
    val redirectUri: String,
    val code: String,
    val state: String? = null,
    val scope: String? = null,
    val responseMode: ResponseModeType = ResponseModeType.QUERY,
    val extraParameters: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
)

sealed class AuthorizeResponseResult {
    data class Success(val response: AuthorizeResponse) : AuthorizeResponseResult()
    data class Failure(val error: id.walt.openid4vci.errors.OAuthError) : AuthorizeResponseResult()

    fun isSuccess(): Boolean = this is Success
}

data class AuthorizeHttpResponse(
    val status: Int,
    val redirectUri: String?,
    val parameters: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
)