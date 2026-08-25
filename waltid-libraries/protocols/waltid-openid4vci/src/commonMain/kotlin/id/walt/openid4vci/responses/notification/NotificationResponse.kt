package id.walt.openid4vci.responses.notification

import id.walt.openid4vci.errors.NotificationError
import kotlinx.serialization.json.JsonElement

/** Successful Notification Endpoint acknowledgement. The HTTP response has no body. */
data object NotificationResponse

sealed class NotificationResponseResult {
    data class Success(val response: NotificationResponse = NotificationResponse) : NotificationResponseResult()
    data class Failure(val error: NotificationError) : NotificationResponseResult()

    fun isSuccess(): Boolean = this is Success
}

data class NotificationResponseHttp(
    val status: Int,
    val payload: Map<String, JsonElement>? = null,
    val headers: Map<String, String> = emptyMap(),
)
