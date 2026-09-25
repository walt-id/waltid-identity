import id.walt.w3c.issuance.dataFunctions
import id.walt.w3c.utils.CredentialDataMergeUtils.mdocNamespaceMapping
import id.walt.w3c.utils.CredentialDataMergeUtils.mergeMdocPayloadWithMapping
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CredentialDataMergeUtilsMdocTest {

    @Test
    fun `mdoc namespace mapping rejects primitive and unknown keys`() {
        val credentialData = buildJsonObject {
            putJsonObject("org.iso.18013.5.1") { put("given_name", "Jane") }
        }
        val unknown = buildJsonObject {
            putJsonObject("org.iso.18013.5.1.unknown") { put("given_name", "<uuid>") }
        }
        val primitive = buildJsonObject {
            put("org.iso.18013.5.1", "<uuid>")
        }
        val msoHint = buildJsonObject {
            put("validFrom", "<timestamp>")
        }
        val unknownError = assertFailsWith<IllegalArgumentException> { unknown.mdocNamespaceMapping(credentialData) }
        assertTrue(unknownError.message!!.contains("org.iso.18013.5.1.unknown"))
        val primitiveError = assertFailsWith<IllegalArgumentException> { primitive.mdocNamespaceMapping(credentialData) }
        assertTrue(primitiveError.message!!.contains("must be a JSON object"))
        val msoError = assertFailsWith<IllegalArgumentException> { msoHint.mdocNamespaceMapping(credentialData) }
        assertTrue(msoError.message!!.contains("msoData"))
    }

    @Test
    fun `mdoc merge replaces arrays and evaluates nested templates`() = runTest {
        val credentialData = buildJsonObject {
            putJsonObject("org.iso.18013.5.1") {
                putJsonArray("administrative_number") { add("original") }
                put("issuing_authority", "original-authority")
            }
        }
        val mapping = buildJsonObject {
            putJsonObject("org.iso.18013.5.1") {
                put("issuing_authority", "<issuerId>")
                putJsonArray("administrative_number") {
                    add("<issuerId>")
                    add("mapped-value")
                }
            }
        }
        val merged = credentialData.mergeMdocPayloadWithMapping(
            mapping = mapping,
            context = mapOf("issuerId" to JsonPrimitive("https://issuer.example")),
            data = dataFunctions,
        )
        val namespace = merged.getValue("org.iso.18013.5.1") as JsonObject
        assertEquals("https://issuer.example", namespace.getValue("issuing_authority").jsonPrimitive.content)
        assertEquals(
            listOf("https://issuer.example", "mapped-value"),
            namespace.getValue("administrative_number").jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `date functions emit YYYY-MM-DD`() = runTest {
        val credentialData = buildJsonObject {
            putJsonObject("org.iso.18013.5.1") {
                put("issue_date", "2019-10-20")
                put("expiry_date", "2024-10-20")
            }
        }
        val mapping = buildJsonObject {
            putJsonObject("org.iso.18013.5.1") {
                put("issue_date", "<date>")
                put("expiry_date", "<date-in:365d>")
            }
        }
        val merged = credentialData.mergeMdocPayloadWithMapping(
            mapping = mapping,
            context = emptyMap(),
            data = dataFunctions,
        )
        val namespace = merged.getValue("org.iso.18013.5.1") as JsonObject
        val issueDate = namespace.getValue("issue_date").jsonPrimitive.content
        val expiryDate = namespace.getValue("expiry_date").jsonPrimitive.content
        assertEquals(10, issueDate.length, issueDate)
        assertEquals(10, expiryDate.length, expiryDate)
        assertTrue(expiryDate > issueDate)
    }

    @Test
    fun `sdjwt merge would append arrays but mdoc merge replaces them`() = runTest {
        val credentialData = buildJsonObject {
            put("items", buildJsonArray { add("keep") })
        }
        val mapping = buildJsonObject {
            put("items", buildJsonArray { add("<issuerId>") })
        }
        val merged = credentialData.mergeMdocPayloadWithMapping(
            mapping = mapping,
            context = mapOf("issuerId" to JsonPrimitive("issuer")),
            data = dataFunctions,
        )
        val items = merged.getValue("items") as JsonArray
        assertEquals(listOf("issuer"), items.map { it.jsonPrimitive.content })
    }
}
