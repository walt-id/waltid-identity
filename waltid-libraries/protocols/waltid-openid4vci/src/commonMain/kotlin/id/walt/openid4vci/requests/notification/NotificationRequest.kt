package id.walt.openid4vci.requests.notification

/** OpenID4VCI 1.0 Section 11 Notification Request. */
interface NotificationRequest {
    val notificationId: String
    val event: NotificationEvent
    val eventDescription: String?
}
