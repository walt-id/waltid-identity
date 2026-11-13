package id.walt.openid4vci.requests.notification

import id.walt.openid4vci.json.jsonHasDuplicateMembers
import id.walt.openid4vci.validation.DefaultNotificationRequestValidator
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

    private fun parseBody(body: String) =
        (DefaultNotificationRequestValidator().validate(notificationRequestParameters(body)) as? NotificationRequestValidationResult.Success)?.request

    @Test
    fun rejectsNonStringFieldsAndMalformedJsonButIgnoresUnknownFields() {
        for (name in listOf("notification_id", "event", "event_description")) {
            for (value in listOf("null", "1", "true", "[]", "{}")) {
                val fields = mutableMapOf("notification_id" to "\"id\"", "event" to "\"credential_accepted\"")
                fields[name] = value
                val body = fields.entries.joinToString(",", "{", "}") { "\"${it.key}\":${it.value}" }
                assertTrue(notificationRequestParameters(body).isEmpty(), body)
            }
        }
        for (body in listOf("[]", "null", "{", "{notification_id:id}", "{\"event\":\"credential_accepted\",}",
            "{\"event\":\"credential_accepted\",\"ev\\u0065nt\":\"credential_deleted\"}")) {
            assertTrue(notificationRequestParameters(body).isEmpty(), body)
        }
        assertEquals(NotificationEvent.CREDENTIAL_ACCEPTED, parseBody(
            """{"notification_id":"id","event":"credential_accepted","future":{"nested":[1,true,null]}}"""
        )?.event)
        assertTrue(notificationRequestParameters(
            """{"notification_id":"id","event":"credential_accepted","future":{"x":1,"x":2}}"""
        ).isEmpty())
    }

    @Test
    fun serializesNotificationRequestUsingProtocolFieldAndEventNames() {
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
            val decoded = json.decodeFromString<DefaultNotificationRequest>(encoded)
            assertEquals(DefaultNotificationRequest("notification-id", event, "Wallet result"), decoded)
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
            DefaultNotificationRequest(
                notificationId = "notification-id",
                event = NotificationEvent.CREDENTIAL_ACCEPTED,
            )
        )

        assertEquals(setOf("notification_id", "event"), json.parseToJsonElement(encoded).jsonObject.keys)
        assertNull(json.decodeFromString<DefaultNotificationRequest>(encoded).eventDescription)
    }

    @Test
    fun ignoresUnknownParameters() {
        val request = parseBody(
            """{"notification_id":"notification-id","event":"credential_deleted","future_parameter":"ignored"}"""
        )

        assertEquals(NotificationEvent.CREDENTIAL_DELETED, request?.event)
        assertEquals("notification-id", request?.notificationId)
    }

    @Test
    fun rejectsDuplicateJsonMembers() {
        assertTrue(jsonHasDuplicateMembers("""{"event":"credential_accepted","event":"credential_failure"}"""))
        assertNull(
            parseBody(
                """{"notification_id":"notification-id","event":"credential_accepted","event":"credential_deleted"}"""
            )
        )
        assertFalse(jsonHasDuplicateMembers("""{"notification_id":"notification-id","event":"credential_accepted"}"""))
    }
}
