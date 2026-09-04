package id.walt.openid4vci.requests.notification

import kotlinx.serialization.Serializable

/** OpenID4VCI 1.0 Section 11 Notification Request abstraction. */
@Serializable
sealed interface NotificationRequest {
    val notificationId: String
    val event: NotificationEvent
    val eventDescription: String?
}
