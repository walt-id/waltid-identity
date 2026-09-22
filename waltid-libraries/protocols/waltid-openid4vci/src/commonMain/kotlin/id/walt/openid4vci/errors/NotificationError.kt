package id.walt.openid4vci.errors

import kotlinx.serialization.Serializable

@Serializable
data class NotificationError(
    val error: String,
)

/**
 * OpenID4VCI Notification Endpoint error codes.
 */
object NotificationErrorCodes {
    const val INVALID_NOTIFICATION_ID = "invalid_notification_id"
    const val INVALID_NOTIFICATION_REQUEST = "invalid_notification_request"
}
