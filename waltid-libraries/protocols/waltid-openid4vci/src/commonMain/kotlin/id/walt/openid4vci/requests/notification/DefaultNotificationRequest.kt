package id.walt.openid4vci.requests.notification

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** OpenID4VCI 1.0 Section 11 Notification Request. */
@Serializable
data class DefaultNotificationRequest(
    @SerialName("notification_id")
    override val notificationId: String,

    override val event: NotificationEvent,

    @SerialName("event_description")
    override val eventDescription: String? = null,
) : NotificationRequest
