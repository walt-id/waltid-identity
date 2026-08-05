package id.walt.openid4vci.tokens.access

import id.walt.openid4vci.core.TOKEN_TYPE_BEARER
import id.walt.openid4vci.core.TOKEN_TYPE_DPOP

data class AccessTokenAuthorization(
    val scheme: AccessTokenAuthorizationScheme,
    val token: String,
)

enum class AccessTokenAuthorizationScheme(val value: String) {
    BEARER(TOKEN_TYPE_BEARER),
    DPOP(TOKEN_TYPE_DPOP);

    companion object {
        fun fromValue(value: String): AccessTokenAuthorizationScheme? =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}

fun parseAccessTokenAuthorization(authorizationHeaderValues: List<String>): AccessTokenAuthorization {
    require(authorizationHeaderValues.size == 1) { "Request must contain exactly one Authorization header" }
    val value = authorizationHeaderValues.single().trim()
    val separator = value.indexOfFirst { it.isWhitespace() }
    require(separator > 0) { "Authorization header must contain a scheme and access token" }

    val scheme = AccessTokenAuthorizationScheme.fromValue(value.substring(0, separator))
        ?: throw IllegalArgumentException("Authorization scheme must be Bearer or DPoP")
    val token = value.substring(separator).trim()
    require(token.isNotBlank() && token.none { it.isWhitespace() }) { "Authorization access token is invalid" }

    return AccessTokenAuthorization(scheme = scheme, token = token)
}
