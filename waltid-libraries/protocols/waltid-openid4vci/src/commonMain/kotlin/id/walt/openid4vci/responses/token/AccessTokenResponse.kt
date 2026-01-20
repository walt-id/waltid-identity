package id.walt.openid4vci.responses.token

import id.walt.openid4vci.core.TOKEN_TYPE_BEARER
import id.walt.openid4vci.errors.OAuthError

data class AccessTokenResponse(
    val tokenType: String = TOKEN_TYPE_BEARER,
    val accessToken: String,
    val expiresIn: Long? = null,
    val extra: Map<String, Any?> = emptyMap(),
)

sealed class AccessResponseResult {
    data class Success(val response: AccessTokenResponse) : AccessResponseResult()
    data class Failure(val error: OAuthError) : AccessResponseResult()

    fun isSuccess(): Boolean = this is Success
}

data class AccessHttpResponse(
    val status: Int,
    val payload: Map<String, Any?>,
    val headers: Map<String, String> = emptyMap(),
)
