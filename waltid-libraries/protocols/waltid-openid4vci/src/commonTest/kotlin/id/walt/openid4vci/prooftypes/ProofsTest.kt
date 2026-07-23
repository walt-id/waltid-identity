package id.walt.openid4vci.prooftypes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
}
