package id.walt.events

import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EventSerialNamesTest {

    @Test
    fun `events keep the type names they were stored and exported with before the move`() {
        val subclassNames = Event.serializer().descriptor.getElementDescriptor(1).elementNames.toSet()
        assertEquals(
            listOf(
                "CredentialWalletEvent", "DidEvent", "IssuanceEvent", "IssuanceWalletEvent", "KeyEvent",
                "PresentationWalletEvent", "VerificationEvent",
            ).map { "id.walt.commons.events.$it" }.toSet() + "id.walt.events.UsageEvent",
            subclassNames,
        )
    }

    @Test
    fun `an event stored before the move is read`() {
        val stored = """
            {"type":"id.walt.commons.events.IssuanceEvent","eventType":"IssuanceEvent","_id":"e1","originator":null,
             "organization":"org","target":"org.tenant","timestamp":1,"action":{"type":"issued"},
             "status":{"type":"success"},"callId":null,"error":null,"sessionId":"s1","credentialConfigurationId":"c1",
             "format":"jwt_vc_json"}
        """.trimIndent()

        val event = Json.decodeFromString(Event.serializer(), stored)

        assertIs<IssuanceEvent>(event)
        assertEquals("s1", event.sessionId)
    }
}
