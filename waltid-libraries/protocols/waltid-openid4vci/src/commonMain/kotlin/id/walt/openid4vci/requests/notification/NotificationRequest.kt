package id.walt.openid4vci.requests.notification

import id.walt.openid4vci.errors.NotificationError
import id.walt.openid4vci.errors.NotificationErrorCodes
import id.walt.openid4vci.errors.OAuthError
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** OpenID4VCI 1.0 Section 11 Notification Request. */
@Serializable
data class NotificationRequest(
    @SerialName("notification_id")
    val notificationId: String,

    val event: NotificationEvent,

    @SerialName("event_description")
    val eventDescription: String? = null,
)

sealed class NotificationRequestResult {
    data class Success(
        val request: NotificationRequest,
        val tokenClaims: JsonObject,
    ) : NotificationRequestResult()

    data class Failure(val error: NotificationError) : NotificationRequestResult()

    data class OAuthFailure(val error: OAuthError) : NotificationRequestResult()

    fun isSuccess(): Boolean = this is Success
}

internal fun invalidNotificationRequest(): NotificationRequestResult.Failure =
    NotificationRequestResult.Failure(
        NotificationError(NotificationErrorCodes.INVALID_NOTIFICATION_REQUEST),
    )

/**
 * Parses a Notification Request after the access token has already been verified.
 * Repeated JSON object members are rejected. Unrecognized members are ignored.
 * Returns null when the body is not a valid Notification Request.
 */
internal fun parseNotificationRequest(body: String): NotificationRequest? {
    if (jsonHasDuplicateMembers(body)) return null
    val element = try {
        Json.parseToJsonElement(body)
    } catch (_: Exception) {
        return null
    }
    if (element !is JsonObject) return null

    val notificationId = element["notification_id"]
    if (notificationId !is JsonPrimitive || !notificationId.isString || notificationId.content.isBlank()) {
        return null
    }
    val eventValue = element["event"]
    if (eventValue !is JsonPrimitive || !eventValue.isString) return null
    val event = NotificationEvent.fromWireValue(eventValue.content) ?: return null

    val description = when (val descriptionValue = element["event_description"]) {
        null -> null
        is JsonPrimitive -> {
            if (!descriptionValue.isString) return null
            descriptionValue.content
        }
        else -> return null
    }
    if (description != null && !description.isValidEventDescription()) return null

    return NotificationRequest(
        notificationId = notificationId.content,
        event = event,
        eventDescription = description,
    )
}

/** OpenID4VCI `event_description`: USASCII `%x20-21 / %x23-5B / %x5D-7E`. */
internal fun String.isValidEventDescription(): Boolean =
    all { it in ' '..'!' || it in '#'..'[' || it in ']'..'~' }
