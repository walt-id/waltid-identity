package id.walt.openid4vci.requests.notification

import kotlinx.serialization.Serializable

/**
 * Latest wallet report stored for one issued credential identifier.
 * [notificationId] is the OpenID4VCI identifier from the Credential Response.
 */
@Serializable
data class IssuedCredentialNotification(
    val credentialIdentifier: String,
    val notificationId: String,
    val event: NotificationEvent? = null,
    val eventDescription: String? = null,
)

/** Proposed update; persistence and event delivery remain with the issuer service. */
data class IssuedCredentialNotificationChange(
    val credentialIdentifier: String,
    val event: NotificationEvent,
    val eventDescription: String?,
)
