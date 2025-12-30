package id.walt.openid4vci

import id.walt.openid4vci.core.TOKEN_TYPE_BEARER

sealed class TokenEndpointResult {
    data class Success(
        val accessToken: String,
        val tokenType: String = TOKEN_TYPE_BEARER,
        val extra: Map<String, Any?> = emptyMap(),
    ) : TokenEndpointResult()

    data class Failure(
        val error: String,
        val description: String? = null,
    ) : TokenEndpointResult()

    fun isSuccess(): Boolean = this is Success
}
