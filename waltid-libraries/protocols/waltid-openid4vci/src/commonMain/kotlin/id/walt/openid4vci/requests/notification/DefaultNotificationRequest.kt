package id.walt.openid4vci.requests.notification

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DefaultNotificationRequest(
    @SerialName("notification_id")
    override val notificationId: String,

    @SerialName("event")
    override val event: NotificationEvent,

    @SerialName("event_description")
    override val eventDescription: String? = null,
) : NotificationRequest
