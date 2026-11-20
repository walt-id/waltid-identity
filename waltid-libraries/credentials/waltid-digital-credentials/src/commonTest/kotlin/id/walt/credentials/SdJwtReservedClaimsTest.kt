package id.walt.credentials

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SdJwtReservedClaimsTest {
    @Test
    fun `registered non-disclosable claims are rejected at the root`() = runTest {
        for (name in listOf("iss", "nbf", "exp", "cnf", "vct", "vct#integrity", "aka_vcts", "status")) {
            val disclosure = SdJwtSelectiveDisclosure("salt", name, JsonPrimitive("value"))
            val payload = buildJsonObject {
                if (name != "vct") put("vct", "https://example.com/credential")
                put("_sd", buildJsonArray { add(disclosure.asHashedFromEncoded()) })
            }
            val error = assertFailsWith<IllegalArgumentException> {
                CredentialParser.parseOnly(token(payload, listOf(disclosure)))
            }
            assertTrue(error.message.orEmpty().contains("'$name' must not be selectively disclosed"), error.message)
        }
    }

    @Test
    fun `subclaims of non-disclosable objects and array entries are rejected`() = runTest {
        for (root in listOf("cnf", "status", "aka_vcts")) {
            val disclosure = SdJwtSelectiveDisclosure("salt", if (root == "aka_vcts") null else "value", JsonPrimitive("value"))
            val payload = buildJsonObject {
                put("vct", "https://example.com/credential")
                put(root, if (root == "aka_vcts") buildJsonArray {
                    add(buildJsonObject { put("...", disclosure.asHashedFromEncoded()) })
                } else buildJsonObject {
                    put("_sd", buildJsonArray { add(disclosure.asHashedFromEncoded()) })
                })
            }
            val error = assertFailsWith<IllegalArgumentException> {
                CredentialParser.parseOnly(token(payload, listOf(disclosure)))
            }
            assertTrue(error.message.orEmpty().contains("'$root' must not be selectively disclosed"), error.message)
        }
    }

    @Test
    fun `iat sub and ordinary nested exp remain disclosable and survive serialization`() = runTest {
        val issuedAt = SdJwtSelectiveDisclosure("iat-salt", "iat", JsonPrimitive(1_700_000_000))
        val subject = SdJwtSelectiveDisclosure("sub-salt", "sub", JsonPrimitive("holder"))
        val nested = SdJwtSelectiveDisclosure("nested-salt", "exp", JsonPrimitive("business value"))
        val payload = buildJsonObject {
            put("vct", "https://example.com/credential")
            put("_sd", buildJsonArray {
                add(issuedAt.asHashedFromEncoded())
                add(subject.asHashedFromEncoded())
            })
            put("custom", buildJsonObject { put("_sd", buildJsonArray { add(nested.asHashedFromEncoded()) }) })
        }
        val parsed = CredentialParser.parseOnly(token(payload, listOf(issuedAt, subject, nested)))
        assertEquals(JsonPrimitive(1_700_000_000), parsed.credentialData["iat"])
        assertEquals(JsonPrimitive("holder"), parsed.credentialData["sub"])
        assertEquals(JsonPrimitive("business value"), parsed.credentialData["custom"]?.jsonObject?.get("exp"))
        val reloaded = Json.decodeFromString(DigitalCredential.serializer(), Json.encodeToString(DigitalCredential.serializer(), parsed))
        assertEquals(parsed.credentialData, reloaded.credentialData)
    }

    @Test
    fun `withheld optional claims are not inferred from their digests`() = runTest {
        val undisclosed = SdJwtSelectiveDisclosure("salt", "exp", JsonPrimitive(1_700_000_000))
        val payload = buildJsonObject {
            put("vct", "https://example.com/credential")
            put("_sd", buildJsonArray { add(undisclosed.asHashedFromEncoded()) })
        }
        assertTrue("exp" !in CredentialParser.parseOnly(token(payload, emptyList())).credentialData)
    }

    // Parsing does not verify signatures; these tests exercise disclosure structure only.
    private fun token(payload: JsonObject, disclosures: List<SdJwtSelectiveDisclosure>): String {
        val header = "{\"alg\":\"ES256\",\"typ\":\"dc+sd-jwt\"}".encodeToByteArray().encodeToBase64Url()
        val jwt = "$header.${payload.toString().encodeToByteArray().encodeToBase64Url()}.c2lnbmF0dXJl"
        return (listOf(jwt) + disclosures.map { it.encoded }).joinToString("~", postfix = "~")
    }
}
