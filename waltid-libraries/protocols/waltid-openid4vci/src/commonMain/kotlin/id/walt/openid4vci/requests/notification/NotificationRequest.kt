package id.walt.openid4vci.requests.notification

import kotlinx.serialization.Serializable

/**
 * Notification Request for the Notification Endpoint (interface for extensibility).
**/
@Serializable
sealed interface NotificationRequest {
    val notificationId: String
    val event: NotificationEvent
    val eventDescription: String?
}