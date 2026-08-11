@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.verifier2.handlers.sessioncreation

import id.walt.dcql.models.CredentialFormat
import id.walt.verifier2.data.DcApiAnnexCFlowSetup
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.data.VerificationSessionSetup
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnnexCVerificationSessionCreatorTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun shouldCreateAnnexCSessionWithRequestData() = runTest {
        val setup = json.decodeFromString<VerificationSessionSetup>(
            """
            {
              "flow_type": "dc_api_18013_7",
              "core_flow": {
                "requestedElements": {
                  "org.iso.18013.5.1.mDL": {
                    "org.iso.18013.5.1": [
                      "family_name",
                      "given_name"
                    ]
                  }
                }
              },
              "expectedOrigins": [
                "https://digital-credentials.walt.id"
              ]
            }
            """.trimIndent()
        )

        val annexCSetup = assertIs<DcApiAnnexCFlowSetup>(setup)
        assertEquals("https://digital-credentials.walt.id", annexCSetup.origin)
        assertEquals(1, annexCSetup.expectedOrigins.size)
        assertEquals(
            listOf("family_name", "given_name"),
            annexCSetup.coreFlow.requestedElements!!
                .getValue("org.iso.18013.5.1.mDL")
                .getValue("org.iso.18013.5.1")
        )

        val session = VerificationSessionCreator.createVerificationSession(
            setup = annexCSetup,
            clientId = null,
            urlPrefix = null,
            urlHost = annexCSetup.origin,
        )

        assertEquals(Verification2Session.VerificationSessionStatus.UNUSED, session.status)
        assertNull(session.bootstrapAuthorizationRequestUrl)
        assertNull(session.authorizationRequestUrl)
        assertNotNull(session.ephemeralDecryptionKey)

        val requestData = assertNotNull(session.data).jsonObject
        assertTrue(requestData["protocol"]!!.jsonPrimitive.content.isNotBlank())
        val data = requestData["data"]!!.jsonObject
        assertTrue(data["deviceRequest"]!!.jsonPrimitive.content.isNotBlank())
        assertTrue(data["encryptionInfo"]!!.jsonPrimitive.content.isNotBlank())

        val credential = session.authorizationRequest.dcqlQuery!!.credentials.single()
        assertEquals("org.iso.18013.5.1.mDL", credential.id)
        assertEquals(CredentialFormat.MSO_MDOC, credential.format)
        val claims = credential.claims!!.map { it.path.map { pathSegment -> pathSegment.jsonPrimitive.content } }
        assertEquals(
            listOf(
                listOf("org.iso.18013.5.1", "family_name"),
                listOf("org.iso.18013.5.1", "given_name")
            ),
            claims
        )
    }

    @Test
    fun shouldRequireExactlyOneExpectedOriginForAnnexC() {
        val exception = assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<VerificationSessionSetup>(
                """
                {
                  "flow_type": "dc_api_18013_7",
                  "core_flow": {
                    "requestedElements": {
                      "org.iso.18013.5.1.mDL": {
                        "org.iso.18013.5.1": [
                          "family_name"
                        ]
                      }
                    }
                  },
                  "expectedOrigins": [
                    "https://digital-credentials.walt.id",
                    "https://wallet.example"
                  ]
                }
                """.trimIndent()
            )
        }

        assertTrue(exception.message!!.contains("exactly one expected origin"))
    }

    @Test
    fun shouldRequireRequestedElementsForAnnexC() {
        val exception = assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<VerificationSessionSetup>(
                """
                {
                  "flow_type": "dc_api_18013_7",
                  "core_flow": {},
                  "expectedOrigins": [
                    "https://digital-credentials.walt.id"
                  ]
                }
                """.trimIndent()
            )
        }

        assertTrue(exception.message!!.contains("requestedElements is required"))
    }
}
