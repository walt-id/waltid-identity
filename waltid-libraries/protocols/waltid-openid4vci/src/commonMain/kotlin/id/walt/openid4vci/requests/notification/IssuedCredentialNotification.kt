package id.walt.openid4vci.requests.notification

/**
 * Latest wallet report stored for one issued credential identifier.
 * [notificationId] is the OpenID4VCI identifier from the Credential Response.
 */
data class IssuedCredentialNotification(
    val credentialIdentifier: String,
    val notificationId: String,
    val event: NotificationEvent? = null,
    val eventDescription: String? = null,
)

sealed class IssuedCredentialNotificationUpdate {
    data object UnknownNotificationId : IssuedCredentialNotificationUpdate()
    data object Unchanged : IssuedCredentialNotificationUpdate()
    data class Changed(
        val credentialIdentifier: String,
        val event: NotificationEvent,
        val eventDescription: String?,
    ) : IssuedCredentialNotificationUpdate()
}

/**
 * Resolves a wallet notification against issued credential records.
 * When [authorizedCredentialIdentifiers] is non-null, the matching identifier must be in that set.
 */
fun applyIssuedCredentialNotification(
    issued: Iterable<IssuedCredentialNotification>,
    notificationId: String,
    event: NotificationEvent,
    eventDescription: String?,
    authorizedCredentialIdentifiers: Set<String>? = null,
): IssuedCredentialNotificationUpdate {
    val match = issued.firstOrNull { it.notificationId == notificationId }
        ?: return IssuedCredentialNotificationUpdate.UnknownNotificationId
    if (authorizedCredentialIdentifiers != null && match.credentialIdentifier !in authorizedCredentialIdentifiers) {
        return IssuedCredentialNotificationUpdate.UnknownNotificationId
    }
    if (match.event == event && match.eventDescription == eventDescription) {
        return IssuedCredentialNotificationUpdate.Unchanged
    }
    return IssuedCredentialNotificationUpdate.Changed(
        credentialIdentifier = match.credentialIdentifier,
        event = event,
        eventDescription = eventDescription,
    )
}
