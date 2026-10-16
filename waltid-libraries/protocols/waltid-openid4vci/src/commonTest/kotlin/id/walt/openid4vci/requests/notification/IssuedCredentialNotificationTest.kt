package id.walt.openid4vci.requests.notification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class IssuedCredentialNotificationTest {
    private val issued = listOf(
        IssuedCredentialNotification(
            credentialIdentifier = "openbadge",
            notificationId = "note-openbadge",
            event = NotificationEvent.CREDENTIAL_ACCEPTED,
            eventDescription = "Stored",
        ),
        IssuedCredentialNotification(
            credentialIdentifier = "pid",
            notificationId = "note-pid",
        ),
    )

    @Test
    fun unknownIdAndUnauthorizedIdentifierAreRejected() {
        assertIs<IssuedCredentialNotificationUpdate.UnknownNotificationId>(
            applyIssuedCredentialNotification(
                issued = issued,
                notificationId = "missing",
                event = NotificationEvent.CREDENTIAL_ACCEPTED,
                eventDescription = null,
            )
        )
        assertIs<IssuedCredentialNotificationUpdate.UnknownNotificationId>(
            applyIssuedCredentialNotification(
                issued = issued,
                notificationId = "note-pid",
                event = NotificationEvent.CREDENTIAL_ACCEPTED,
                eventDescription = null,
                authorizedCredentialIdentifiers = setOf("openbadge"),
            )
        )
    }

    @Test
    fun identicalRepeatIsUnchangedAndADifferentEventIsChanged() {
        assertIs<IssuedCredentialNotificationUpdate.Unchanged>(
            applyIssuedCredentialNotification(
                issued = issued,
                notificationId = "note-openbadge",
                event = NotificationEvent.CREDENTIAL_ACCEPTED,
                eventDescription = "Stored",
            )
        )
        val changed = assertIs<IssuedCredentialNotificationUpdate.Changed>(
            applyIssuedCredentialNotification(
                issued = issued,
                notificationId = "note-pid",
                event = NotificationEvent.CREDENTIAL_FAILURE,
                eventDescription = "Could not store",
                authorizedCredentialIdentifiers = setOf("openbadge", "pid"),
            )
        )
        assertEquals("pid", changed.credentialIdentifier)
        assertEquals(NotificationEvent.CREDENTIAL_FAILURE, changed.event)
        assertEquals("Could not store", changed.eventDescription)
    }
}
