package id.walt.verifier2

import id.walt.policies2.vc.CredentialPolicyResult
import id.walt.policies2.vc.policies.RevocationPolicy
import id.walt.verifier2.verification2.MAX_STORED_POLICY_ARRAY
import id.walt.verifier2.verification2.MAX_STORED_POLICY_TEXT
import id.walt.verifier2.verification2.boundedForStorage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `policy_results.vc_policies` held 3,228,057 bytes of a 10,046,803-byte portrait session - a third
 * copy of the credential beside the two deliberate audit copies. Policy results may reference instead
 * of repeat, because `credentialIndex` already identifies the credential.
 */
class StoredPolicyResultBoundsTest {

    private val portrait = "A".repeat(333_334)

    @Test
    fun aBulkStringBecomesADescriptor() {
        val bounded = JsonPrimitive(portrait).boundedForStorage().jsonObject
        assertEquals(333_334, bounded["length"]!!.jsonPrimitive.content.toInt())
        assertEquals(true, bounded["truncated"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(MAX_STORED_POLICY_TEXT, bounded["prefix"]!!.jsonPrimitive.content.length)
        assertTrue(bounded.toString().length < 2_000, "descriptor must not carry the payload")
    }

    @Test
    fun smallValuesSurviveUntouched() {
        // Element values are what diagnose an mdoc serialisation problem, so they must be kept.
        val kept = buildJsonObject {
            put("family_name", "Doe")
            put("age_over_18", true)
            put("birth_date", "1985-03-15")
            put("issue_count", 7)
        }
        assertEquals(kept, kept.boundedForStorage())
    }

    @Test
    fun aBulkStringNestedInsideAnObjectIsFound() {
        // The portrait arrives as a leaf under namespace and element keys, never at the top.
        val nested = buildJsonObject {
            put("org.iso.18013.5.1", buildJsonObject {
                put("given_name", "Test")
                put("portrait", portrait)
            })
        }
        val bounded = nested.boundedForStorage().jsonObject
        val ns = bounded["org.iso.18013.5.1"]!!.jsonObject
        assertEquals("Test", ns["given_name"]!!.jsonPrimitive.content)
        assertEquals(333_334, ns["portrait"]!!.jsonObject["length"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun aLongNumberArrayIsBounded() {
        // A decoded CBOR byte string arrives as an array of numbers.
        val bytes = buildJsonArray { repeat(5_000) { add(JsonPrimitive(it % 256)) } }
        val bounded = bytes.boundedForStorage().jsonObject
        assertEquals(5_000, bounded["length"]!!.jsonPrimitive.content.toInt())
        assertEquals(MAX_STORED_POLICY_ARRAY, (bounded["prefix"] as JsonArray).size)
    }

    @Test
    fun aShortArraySurvivesWhole() {
        val short = buildJsonArray { repeat(10) { add(JsonPrimitive(it)) } }
        assertEquals(short, short.boundedForStorage())
    }

    @Test
    fun policyVerdictFieldsAreNeverChanged() {
        val result = CredentialPolicyResult(
            policy = RevocationPolicy(),
            success = false,
            result = JsonPrimitive(portrait),
            error = "signature mismatch",
            queryId = "my_mdl",
            credentialIndex = 3,
        ).boundedForStorage()

        assertEquals(false, result.success)
        assertEquals("signature mismatch", result.error)
        assertEquals("my_mdl", result.queryId)
        assertEquals(3, result.credentialIndex)
        assertTrue(result.result!!.jsonObject.containsKey("truncated"))
    }

    @Test
    fun aResultlessPolicyIsLeftAlone() {
        val result = CredentialPolicyResult(
            policy = RevocationPolicy(), success = true, result = null, credentialIndex = 0,
        ).boundedForStorage()
        assertNull(result.result)
        assertEquals(true, result.success)
    }
}
