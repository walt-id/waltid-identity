import id.walt.w3c.vc.vcs.W3CV2JsonLd
import id.walt.w3c.vc.vcs.toW3CV2Credential
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class W3CV2JsonLdTest {

    @Test
    fun normalizesCompactedJsonLdAliasesToW3CV2Credential() {
        val jsonLd = Json.decodeFromString<JsonObject>(
            """
            {
              "@context": "https://www.w3.org/ns/credentials/v2",
              "@id": "urn:uuid:123",
              "@type": "VerifiableCredential",
              "issuer": {
                "@id": "did:example:issuer",
                "name": "Example Issuer"
              },
              "validFrom": "2025-05-15T00:00:00Z",
              "credentialSubject": [{
                "@id": "did:example:subject",
                "@type": "ExamplePersonCredential",
                "name": "Jane Doe"
              }]
            }
            """.trimIndent()
        )

        val credential = jsonLd.toW3CV2Credential()
        val data = credential.toJsonObject()

        assertTrue(credential.isV2())
        assertEquals("urn:uuid:123", data["id"]!!.jsonPrimitive.content)
        assertEquals("VerifiableCredential", data["type"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals("did:example:issuer", data["issuer"]!!.jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("did:example:subject", data["credentialSubject"]!!.jsonArray.single().jsonObject["id"]!!.jsonPrimitive.content)
    }

    @Test
    fun rejectsV2CredentialWithoutBaseContext() {
        val jsonLd = Json.decodeFromString<JsonObject>(
            """
            {
              "@context": ["https://example.org/context"],
              "type": ["VerifiableCredential"],
              "issuer": "did:example:issuer",
              "credentialSubject": { "id": "did:example:subject" }
            }
            """.trimIndent()
        )

        assertFailsWith<IllegalArgumentException> {
            W3CV2JsonLd.toCredential(jsonLd)
        }
    }
}
