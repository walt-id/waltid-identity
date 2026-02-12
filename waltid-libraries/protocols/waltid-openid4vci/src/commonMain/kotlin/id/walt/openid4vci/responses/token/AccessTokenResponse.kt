package id.walt.openid4vci.responses.token

import id.walt.openid4vci.core.TOKEN_TYPE_BEARER
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.requests.token.AccessTokenRequest

data class AccessTokenResponse(
    val tokenType: String = TOKEN_TYPE_BEARER,
    val accessToken: String,
    val expiresIn: Long? = null,
    val extra: Map<String, Any?> = emptyMap(),
)

sealed class AccessTokenResponseResult {
    data class Success(
        val request: AccessTokenRequest,
        val response: AccessTokenResponse,
    ) : AccessTokenResponseResult()
    data class Failure(val error: OAuthError) : AccessTokenResponseResult()

    fun isSuccess(): Boolean = this is Success
}

data class AccessTokenResponseHttp(
    val status: Int,
    val payload: Map<String, Any?>,
    val headers: Map<String, String> = emptyMap(),
)
