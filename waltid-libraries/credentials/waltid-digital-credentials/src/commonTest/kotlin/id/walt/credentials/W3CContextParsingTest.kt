package id.walt.credentials

import id.walt.credentials.formats.W3C2
import id.walt.credentials.CredentialDetectorTypes.CredentialPrimaryDataType
import id.walt.credentials.CredentialDetectorTypes.SignaturePrimaryType
import id.walt.credentials.CredentialDetectorTypes.W3CSubType
import id.walt.w3c.vc.vcs.W3CVC
import id.walt.w3c.vc.vcs.applyIssuedV2Context
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class W3CContextParsingTest {

    @Test
    fun inlineContextObjectDoesNotPreventV2Detection() = runTest {
        val credential = """
            {
              "@context": [
                "https://www.w3.org/ns/credentials/v2",
                "https://w3id.org/gaia-x/development#",
                {
                  "vcard": "http://www.w3.org/2006/vcard/ns#",
                  "schema": "https://schema.org/"
                }
              ],
              "type": ["VerifiableCredential", "gx:LegalPerson"],
              "credentialSubject": { "id": "did:example:subject" }
            }
        """.trimIndent()

        assertEquals(W3CSubType.W3C_2, CredentialParser.detectW3CDataModelVersion(Json.parseToJsonElement(credential).jsonObject))

        val (detection, parsed) = CredentialParser.detectAndParse(credential)
        assertEquals(CredentialPrimaryDataType.W3C, detection.credentialPrimaryType)
        assertEquals(W3CSubType.W3C_2, detection.credentialSubType)
        assertEquals(SignaturePrimaryType.UNSIGNED, detection.signaturePrimary)
        assertIs<W3C2>(parsed)
    }

    @Test
    fun inlineContextObjectDoesNotPreventV1Detection() {
        val credential = Json.parseToJsonElement(
            """
            {
              "@context": [
                "https://www.w3.org/2018/credentials/v1",
                { "gx": "https://w3id.org/gaia-x/development#" }
              ],
              "type": ["VerifiableCredential", "gx:LegalPerson"]
            }
            """.trimIndent()
        ).jsonObject

        assertEquals(W3CSubType.W3C_1_1, CredentialParser.detectW3CDataModelVersion(credential))
    }

    @Test
    fun contextArrayWithOnlyObjectsIsAnUnknownContext() {
        val credential = Json.parseToJsonElement(
            """
            {
              "@context": [{ "gx": "https://w3id.org/gaia-x/development#" }],
              "type": ["gx:LegalPerson"]
            }
            """.trimIndent()
        ).jsonObject

        assertFailsWith<IllegalStateException> {
            CredentialParser.detectW3CDataModelVersion(credential)
        }
    }

    @Test
    fun jwtVcClaimWithInlineContextObjectParsesAsV2() = runTest {
        val jwt = "eyJhbGciOiJub25lIn0.eyJ2YyI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvbnMvY3JlZGVudGlhbHMvdjIiLCJodHRwczovL3czaWQub3JnL2dhaWEteC9kZXZlbG9wbWVudCMiLHsidmNhcmQiOiJodHRwOi8vd3d3LnczLm9yZy8yMDA2L3ZjYXJkL25zIyIsInNjaGVtYSI6Imh0dHBzOi8vc2NoZW1hLm9yZy8ifV0sInR5cGUiOlsiVmVyaWZpYWJsZUNyZWRlbnRpYWwiLCJneDpMZWdhbFBlcnNvbiJdLCJjcmVkZW50aWFsU3ViamVjdCI6eyJpZCI6ImRpZDpleGFtcGxlOnN1YmplY3QifX19.c2ln"

        val (detection, parsed) = CredentialParser.detectAndParse(jwt)
        assertEquals(CredentialPrimaryDataType.W3C, detection.credentialPrimaryType)
        assertEquals(W3CSubType.W3C_2, detection.credentialSubType)
        assertEquals(SignaturePrimaryType.JWT, detection.signaturePrimary)
        assertIs<W3C2>(parsed)
    }

    @Test
    fun v2UpgradeKeepsInlineContextObjects() {
        val credential = Json.parseToJsonElement(
            """
            {
              "@context": [
                "https://www.w3.org/2018/credentials/v1",
                { "gx": "https://w3id.org/gaia-x/development#" }
              ],
              "type": ["VerifiableCredential", "gx:LegalPerson"],
              "issuanceDate": "2020-01-01T00:00:00Z"
            }
            """.trimIndent()
        ).jsonObject.toMutableMap()

        assertTrue(
            W3CVC(
                Json.parseToJsonElement(
                    """
                    {
                      "@context": [
                        { "gx": "https://w3id.org/gaia-x/development#" },
                        "https://www.w3.org/ns/credentials/v2"
                      ],
                      "type": ["VerifiableCredential"]
                    }
                    """.trimIndent()
                ).jsonObject
            ).isV2()
        )

        credential.applyIssuedV2Context()
        val context = credential.getValue("@context").jsonArray
        assertEquals("https://www.w3.org/ns/credentials/v2", context[0].jsonPrimitive.content)
        assertEquals("https://w3id.org/gaia-x/development#", (context[1] as JsonObject).getValue("gx").jsonPrimitive.content)
        assertEquals("2020-01-01T00:00:00Z", credential.getValue("validFrom").jsonPrimitive.content)
        assertNull(credential["issuanceDate"])
    }
}
