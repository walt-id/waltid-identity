package id.walt.openid4vci.prooftypes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class ProofsTest {
    @Test
    fun serializesOnlyWireProofTypes() {
        val serialized = Json.encodeToJsonElement(
            Proofs.serializer(),
            Proofs(jwt = listOf("proof")),
        ).jsonObject

        assertEquals(listOf("proof"), Proofs.fromJsonObject(serialized).jwt)
        assertFalse("additional" in serialized)
    }

    @Test
    fun rejectsUnsupportedProofTypes() {
        assertFailsWith<IllegalArgumentException> {
            Proofs.fromJsonObject(Json.parseToJsonElement("""{"example_proof":["proof"]}""").jsonObject)
        }
    }
}
