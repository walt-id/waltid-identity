package id.walt.openid4vci.responses.notification

import id.walt.openid4vci.core.TOKEN_TYPE_BEARER
import id.walt.openid4vci.core.TOKEN_TYPE_DPOP
import id.walt.openid4vci.errors.NotificationError
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.errors.OAuthErrorCodes
import id.walt.openid4vci.tokens.access.AccessTokenAuthorizationScheme
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** Successful Notification Endpoint acknowledgement. The HTTP response has no body. */
data object NotificationResponse

sealed class NotificationResponseResult {
    data class Success(val response: NotificationResponse = NotificationResponse) : NotificationResponseResult()

    fun isSuccess(): Boolean = this is Success
}

data class NotificationResponseHttp(
    val status: Int,
    val payload: Map<String, JsonElement>? = null,
    val headers: Map<String, String> = emptyMap(),
)

private const val WWW_AUTHENTICATE_HEADER = "WWW-Authenticate"

private val NO_STORE_HEADERS = mapOf(
    "Cache-Control" to "no-store",
    "Pragma" to "no-cache",
)

internal fun notificationErrorHttp(error: NotificationError): NotificationResponseHttp =
    NotificationResponseHttp(
        status = 400,
        payload = mapOf("error" to JsonPrimitive(error.error)),
        headers = NO_STORE_HEADERS,
    )

internal fun notificationOAuthErrorHttp(
    error: OAuthError,
    scheme: AccessTokenAuthorizationScheme,
): NotificationResponseHttp {
    val headers = when (error.error) {
        OAuthErrorCodes.INVALID_TOKEN,
        OAuthErrorCodes.INVALID_DPOP_PROOF,
        -> NO_STORE_HEADERS + (WWW_AUTHENTICATE_HEADER to authenticationChallenge(error, scheme))

        else -> NO_STORE_HEADERS
    }
    return NotificationResponseHttp(
        status = notificationOAuthStatus(error),
        payload = buildMap {
            put("error", JsonPrimitive(error.error))
            error.description?.let { put("error_description", JsonPrimitive(it)) }
        },
        headers = headers,
    )
}

internal fun notificationSuccessHttp(): NotificationResponseHttp =
    NotificationResponseHttp(status = 204, headers = NO_STORE_HEADERS)

private fun notificationOAuthStatus(error: OAuthError): Int = when (error.error) {
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
