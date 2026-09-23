package id.walt.openid4vci.requests.notification

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationRequestTest {
    private val json = Json { explicitNulls = false }

    @Test
    fun serializesNotificationRequestUsingProtocolFieldAndEventNames() {
        val events = mapOf(
            NotificationEvent.CREDENTIAL_ACCEPTED to "credential_accepted",
            NotificationEvent.CREDENTIAL_FAILURE to "credential_failure",
            NotificationEvent.CREDENTIAL_DELETED to "credential_deleted",
        )

        events.forEach { (event, wireName) ->
            val encoded = json.encodeToString(
                NotificationRequest(
                    notificationId = "notification-id",
                    event = event,
                    eventDescription = "Wallet result",
                )
            )
            val body = json.parseToJsonElement(encoded).jsonObject

            assertEquals("notification-id", body["notification_id"]?.jsonPrimitive?.content)
            assertEquals(wireName, body["event"]?.jsonPrimitive?.content)
            assertEquals("Wallet result", body["event_description"]?.jsonPrimitive?.content)
            assertEquals(setOf("notification_id", "event", "event_description"), body.keys)
        }
    }

    @Test
    fun omitsAbsentEventDescription() {
        val encoded = json.encodeToString(
            NotificationRequest(
                notificationId = "notification-id",
                event = NotificationEvent.CREDENTIAL_ACCEPTED,
            )
        )

        assertFalse("event_description" in json.parseToJsonElement(encoded).jsonObject)
    }

    @Test
    fun rejectsInvalidNotificationRequestValues() {
        assertNull(parseNotificationRequest("""{"notification_id":" ","event":"credential_accepted"}"""))
        assertNull(
            parseNotificationRequest(
                """{"notification_id":"notification-id","event":"credential_failure","event_description":"unsupported\nline break"}"""
            )
        )
        assertNull(parseNotificationRequest("""{"notification_id":"notification-id","event":"unsupported_event"}"""))
        assertNull(parseNotificationRequest("{"))
    }

    @Test
    fun ignoresUnknownParameters() {
        val request = parseNotificationRequest(
            """{"notification_id":"notification-id","event":"credential_deleted","future_parameter":"ignored"}"""
        )

        assertEquals(NotificationEvent.CREDENTIAL_DELETED, request?.event)
        assertEquals("notification-id", request?.notificationId)
    }

    @Test
    fun rejectsDuplicateJsonMembers() {
        assertTrue(jsonHasDuplicateMembers("""{"event":"credential_accepted","event":"credential_failure"}"""))
        assertNull(
            parseNotificationRequest(
                """{"notification_id":"notification-id","event":"credential_accepted","event":"credential_deleted"}"""
            )
        )
        assertFalse(jsonHasDuplicateMembers("""{"notification_id":"notification-id","event":"credential_accepted"}"""))
    }
}
