package id.walt.openid4vci.errors

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationErrorTest {
    private val json = Json { explicitNulls = false }

    @Test
    fun `serializes invalid notification id error`() {
        val encoded = json.encodeToString(
            NotificationError(error = NotificationErrorCodes.INVALID_NOTIFICATION_ID)
        )
        val body = json.parseToJsonElement(encoded).jsonObject

        assertEquals("invalid_notification_id", body["error"]?.jsonPrimitive?.content)
        assertEquals(setOf("error"), body.keys)
    }

    @Test
    fun `serializes invalid notification request without optional description`() {
        val encoded = json.encodeToString(
            NotificationError(error = NotificationErrorCodes.INVALID_NOTIFICATION_REQUEST)
        )
        val body = json.parseToJsonElement(encoded).jsonObject

        assertEquals("invalid_notification_request", body["error"]?.jsonPrimitive?.content)
        assertEquals(setOf("error"), body.keys)
    }
}
