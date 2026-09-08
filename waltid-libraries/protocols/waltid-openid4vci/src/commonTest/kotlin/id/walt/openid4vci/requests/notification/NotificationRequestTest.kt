package id.walt.openid4vci.requests.notification

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class NotificationRequestTest {
    private val json = Json { explicitNulls = false }

    @Test
    fun `serializes notification request using protocol field and event names`() {
        val events = mapOf(
            NotificationEvent.CREDENTIAL_ACCEPTED to "credential_accepted",
            NotificationEvent.CREDENTIAL_FAILURE to "credential_failure",
            NotificationEvent.CREDENTIAL_DELETED to "credential_deleted",
        )

        events.forEach { (event, wireName) ->
            val encoded = json.encodeToString(
                DefaultNotificationRequest(
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
    fun `omits absent event description`() {
        val encoded = json.encodeToString(
            DefaultNotificationRequest(
                notificationId = "notification-id",
                event = NotificationEvent.CREDENTIAL_ACCEPTED,
            )
        )

        assertFalse("event_description" in json.parseToJsonElement(encoded).jsonObject)
    }

    @Test
    fun `rejects invalid notification request values`() {
        assertFailsWith<IllegalArgumentException> {
            DefaultNotificationRequest(" ", NotificationEvent.CREDENTIAL_ACCEPTED)
        }
        assertFailsWith<IllegalArgumentException> {
            DefaultNotificationRequest(
                "notification-id",
                NotificationEvent.CREDENTIAL_FAILURE,
                "unsupported\nline break",
            )
        }
    }
}
