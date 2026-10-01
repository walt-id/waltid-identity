package id.walt.openid4vci.responses

import id.walt.openid4vci.core.TOKEN_TYPE_BEARER
import id.walt.openid4vci.core.TOKEN_TYPE_DPOP
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.errors.OAuthErrorCodes
import id.walt.openid4vci.tokens.access.AccessTokenAuthorizationScheme
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

internal const val WWW_AUTHENTICATE_HEADER = "WWW-Authenticate"

internal val NO_STORE_HEADERS = mapOf(
    "Cache-Control" to "no-store",
    "Pragma" to "no-cache",
)

internal data class ProtectedResourceOAuthErrorHttp(
    val status: Int,
    val payload: Map<String, JsonElement>,
    val headers: Map<String, String>,
)

internal fun protectedResourceOAuthErrorHttp(
    error: OAuthError,
    scheme: AccessTokenAuthorizationScheme,
): ProtectedResourceOAuthErrorHttp {
    val headers = when (error.error) {
        OAuthErrorCodes.INVALID_TOKEN,
        OAuthErrorCodes.INVALID_DPOP_PROOF,
        -> NO_STORE_HEADERS + (WWW_AUTHENTICATE_HEADER to authenticationChallenge(error, scheme))

        else -> NO_STORE_HEADERS
    }
    return ProtectedResourceOAuthErrorHttp(
        status = protectedResourceOAuthStatus(error),
        payload = buildMap {
            put("error", JsonPrimitive(error.error))
            error.description?.let { put("error_description", JsonPrimitive(it)) }
        },
        headers = headers,
    )
}

internal fun protectedResourceOAuthStatus(error: OAuthError): Int = when (error.error) {
    OAuthErrorCodes.INVALID_TOKEN,
    OAuthErrorCodes.INVALID_DPOP_PROOF,
    OAuthErrorCodes.INVALID_CLIENT,
    -> 401

    OAuthErrorCodes.INSUFFICIENT_SCOPE -> 403
    OAuthErrorCodes.SERVER_ERROR,
    OAuthErrorCodes.TEMPORARILY_UNAVAILABLE,
    -> 500

    else -> 400
}

private fun authenticationChallenge(error: OAuthError, scheme: AccessTokenAuthorizationScheme): String {
    val schemeName = when (scheme) {
        AccessTokenAuthorizationScheme.BEARER -> TOKEN_TYPE_BEARER
        AccessTokenAuthorizationScheme.DPOP -> TOKEN_TYPE_DPOP
    }
    return buildString {
        append(schemeName)
        append(" error=\"").append(error.error.escapeAuthenticationParameter()).append('"')
        error.description?.let { description ->
            append(", error_description=\"")
                .append(description.escapeAuthenticationParameter())
                .append('"')
        }
    }
}

private fun String.escapeAuthenticationParameter(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")
