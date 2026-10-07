package id.walt.credentials

import id.walt.credentials.formats.W3C2
import id.walt.credentials.CredentialDetectorTypes.CredentialPrimaryDataType
import id.walt.credentials.CredentialDetectorTypes.SignaturePrimaryType
import id.walt.credentials.CredentialDetectorTypes.W3CSubType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

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
}
