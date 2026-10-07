package id.walt.openid4vci.proofs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class ProofsTest {
    @Test
    fun serializesOnlyWireProofTypes() {
        val cases = listOf(
            ProofType.JWT to """{"jwt":["proof"]}""",
            ProofType.DI_VP to """{"di_vp":[{"proof":"example"}]}""",
            ProofType.ATTESTATION to """{"attestation":["proof"]}""",
        )
        for ((type, wireJson) in cases) {
            val expected = Json.parseToJsonElement(wireJson).jsonObject
            val proofs = Proofs.fromJsonObject(expected)
            assertEquals(type, proofs.normalized().type)
            assertEquals(expected, Json.encodeToJsonElement(Proofs.serializer(), proofs).jsonObject)
            assertEquals(proofs, Json.decodeFromJsonElement(Proofs.serializer(), expected))
        }
    }

    @Test
    fun rejectsUnsupportedProofTypes() {
        assertFailsWith<IllegalArgumentException> {
            Proofs.fromJsonObject(Json.parseToJsonElement("""{"example_proof":["proof"]}""").jsonObject)
        }
    }
}
