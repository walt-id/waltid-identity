package id.walt.openid4vci.responses.notification

import id.walt.openid4vci.errors.NotificationError
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.responses.NO_STORE_HEADERS
import id.walt.openid4vci.responses.protectedResourceOAuthErrorHttp
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
    val oauth = protectedResourceOAuthErrorHttp(error, scheme)
    return NotificationResponseHttp(
        status = oauth.status,
        payload = oauth.payload,
        headers = oauth.headers,
    )
}

internal fun notificationSuccessHttp(): NotificationResponseHttp =
    NotificationResponseHttp(status = 204, headers = NO_STORE_HEADERS)
