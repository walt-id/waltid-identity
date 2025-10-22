@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.tests

import id.walt.commons.testing.utils.ServiceTestUtils.loadResource
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.issuer.issuance.openapi.issuerapi.MdocDocs
import id.walt.oid4vc.data.ResponseMode
import id.walt.sdjwt.SDField
import id.walt.sdjwt.SDMap
import id.walt.test.integration.assertContainsPresentationDefinitionUri
import id.walt.test.integration.expectFailure
import id.walt.test.integration.loadJsonResource
import id.walt.verifier.oidc.models.presentedcredentials.*
import id.walt.verifier.openapi.VerifierApiExamples
import id.walt.w3c.utils.VCFormat
import id.walt.webwallet.web.controllers.exchange.UsePresentationRequest
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertNull
import kotlin.collections.first
import kotlin.test.*
import kotlin.text.isBlank
import kotlin.text.isNotBlank
import kotlin.text.toByteArray
import kotlin.text.trimIndent
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@TestMethodOrder(OrderAnnotation::class)
class VerifierPresentedCredentialsIntegrationTests : AbstractIntegrationTest() {

    companion object {
        val issuerKey = loadJsonResource("issuance/key.json")
        val issuerDid = loadResource("issuance/did.txt")

        private val universityDegreeNoDisclosuresIssuanceRequest = Json.decodeFromString<IssuanceRequest>(
            """
        {
          "issuerKey": {
            "type": "jwk",
            "jwk": {
              "kty": "OKP",
              "d": "JvJIpga2GD8LJeRu4Sv-mL4thE31DuFlr9PA04CIoZY",
              "crv": "Ed25519",
              "kid": "iJMS5bkZVIlncfq_Lf_SuxJ2JtQ5Hvaz7tWPnAjUUds",
              "x": "FZdvwC8aGhRwqzWptej0NZgtwYAI1SyFg1mKDETOfqE"
            }
          },
          "issuerDid": "did:jwk:eyJrdHkiOiJPS1AiLCJjcnYiOiJFZDI1NTE5Iiwia2lkIjoiaUpNUzVia1pWSWxuY2ZxX0xmX1N1eEoySnRRNUh2YXo3dFdQbkFqVVVkcyIsIngiOiJGWmR2d0M4YUdoUndxeldwdGVqME5aZ3R3WUFJMVN5RmcxbUtERVRPZnFFIn0",
          "credentialConfigurationId": "UniversityDegree_jwt_vc_json",
          "credentialData": {
            "@context": [
              "https://www.w3.org/2018/credentials/v1",
              "https://www.w3.org/2018/credentials/examples/v1"
            ],
            "id": "http://example.gov/credentials/3732",
            "type": [
              "VerifiableCredential",
              "UniversityDegree"
            ],
            "issuer": {
              "id": "did:web:vc.transmute.world"
            },
            "issuanceDate": "2020-03-10T04:24:12.164Z",
            "credentialSubject": {
              "id": "did:example:ebfeb1f712ebc6f1c276e12ec21",
              "degree": {
                "type": "BachelorDegree",
                "name": "Bachelor of Science and Arts"
              }
            }
          },
          "mapping": {
            "id": "<uuid>",
            "issuerDid": "<issuerDid>",
            "issuer": {
              "id": "<issuerDid>"
            },
            "credentialSubject": {
              "id": "<subjectDid>"
            },
            "issuanceDate": "<timestamp>",
            "expirationDate": "<timestamp-in:365d>"
          },
          "authenticationMethod": "PRE_AUTHORIZED",
          "standardVersion": "DRAFT13"
        }
    """.trimIndent()
        ).copy(
            issuerKey = issuerKey,
            issuerDid = issuerDid,
        )

        private val universityDegreeNoDisclosurePresentationRequest = buildJsonObject {
            put("request_credentials", buildJsonArray {
                addJsonObject {
                    put("format", VCFormat.jwt_vc_json.toJsonElement())
                    put("type", "UniversityDegreeCredential".toJsonElement())
                }
            })
        }

        private val openBadgeNoDisclosuresIssuanceRequest = Json.decodeFromString<IssuanceRequest>(
            string = loadResource("issuance/openbadgecredential-issuance-request.json")
        ).copy(
            issuerKey = issuerKey,
            issuerDid = issuerDid,
        )
        private val openBadgeNoDisclosurePresentationRequest = buildJsonObject {
            put("request_credentials", buildJsonArray {
                addJsonObject {
                    put("format", VCFormat.jwt_vc_json.toJsonElement())
                    put("type", "OpenBadgeCredential".toJsonElement())
                }
            })
        }

        private val universityDegreeWithDisclosuresIssuanceRequest = universityDegreeNoDisclosuresIssuanceRequest.copy(
            credentialData = universityDegreeNoDisclosuresIssuanceRequest.credentialData,
            selectiveDisclosure = SDMap(
                fields = mapOf(
                    "issuanceDate" to SDField(true),
                    "credentialSubject" to SDField(
                        sd = false,
                        children = SDMap(
                            fields = mapOf(
                                "degree" to SDField(
                                    sd = false,
                                    children = SDMap(
                                        fields = mapOf(
                                            "name" to SDField(true),
                                        )
                                    ),
                                ),
                            )
                        ),
                    ),
                ),
            ),
        )
        private val universityDegreeWithDisclosuresPresentationRequest = Json.decodeFromString<JsonObject>(
            """
        {
            "vp_policies": [
                "signature",
                "expired",
                "not-before",
                "presentation-definition"
            ],
            "vc_policies": [
                "signature",
                "expired",
                "not-before"
            ],
            "request_credentials": [
                {
                    "type": "UniversityDegreeCredential",
                    "input_descriptor": {
                        "id": "some-id-id",
                        "format": {
                            "jwt_vc_json": {}
                        },
                        "constraints": {
                            "fields": [
                                {
                                    "path": [
                                        "${'$'}.vc.issuanceDate"
                                    ],
                                    "filter": {
                                        "type": "string",
                                        "pattern": ".*"
                                    }
                                },
                                {
                                    "path": [
                                        "${'$'}.vc.credentialSubject.degree.name"
                                    ],
                                    "filter": {
                                        "type": "string",
                                        "pattern": ".*"
                                    }
                                }                                
                            ],
                            "limit_disclosure": "required"
                        }
                    }
                }
            ]
        }
    """.trimIndent()
        )

        private val openBadgeWithDisclosuresIssuanceRequest = openBadgeNoDisclosuresIssuanceRequest.copy(
            credentialData = openBadgeNoDisclosuresIssuanceRequest.credentialData,
            selectiveDisclosure = SDMap(
                fields = mapOf(
                    "issuanceDate" to SDField(true),
                    "name" to SDField(true),
                ),
            ),
        )
        private val openBadgeWithDisclosuresPresentationRequest = Json.decodeFromString<JsonObject>(
            """
        {
            "vp_policies": [
                "signature",
                "expired",
                "not-before",
                "presentation-definition"
            ],
            "vc_policies": [
                "signature",
                "expired",
                "not-before"
            ],
            "request_credentials": [
                {
                    "type": "OpenBadgeCredential",
                    "input_descriptor": {
                        "id": "some-id-id",
                        "format": {
                            "jwt_vc_json": {}
                        },
                        "constraints": {
                            "fields": [
                                {
                                    "path": [
                                        "${'$'}.vc.issuanceDate"
                                    ],
                                    "filter": {
                                        "type": "string",
                                        "pattern": ".*"
                                    }
                                },
                                {
                                    "path": [
                                        "${'$'}.vc.expirationDate"
                                    ],
                                    "filter": {
                                        "type": "string",
                                        "pattern": ".*"
                                    }
                                }                                
                            ],
                            "limit_disclosure": "required"
                        }
                    }
                }
            ]
        }
    """.trimIndent()
        )

        private val sdJwtVcIssuanceRequest = Json.decodeFromString<IssuanceRequest>(
            """
        {
            "issuerKey": {
                "type": "jwk",
                "jwk": {
                    "kty": "EC",
                    "d": "KJ4k3Vcl5Sj9Mfq4rrNXBm2MoPoY3_Ak_PIR_EgsFhQ",
                    "crv": "P-256",
                    "x": "G0RINBiF-oQUD3d5DGnegQuXenI29JDaMGoMvioKRBM",
                    "y": "ed3eFGs2pEtrp7vAZ7BLcbrUtpKkYWAT2JPUQK4lN4E"
                }
            },
            "credentialConfigurationId": "identity_credential_vc+sd-jwt",
            "credentialData": {
                "given_name": "John",
                "family_name": "Doe",
                "email": "johndoe@example.com",
                "phone_number": "+1-202-555-0101",
                "address": {
                    "street_address": "123 Main St",
                    "locality": "Anytown",
                    "region": "Anystate",
                    "country": "US"
                },
                "birthdate": "1940-01-01",
                "is_over_18": true,
                "is_over_21": true,
                "is_over_65": true
            },
            "mapping": {
                "id": "<uuid>",
                "iat": "<timestamp-seconds>",
                "nbf": "<timestamp-seconds>",
                "exp": "<timestamp-in-seconds:365d>"
            },
            "selectiveDisclosure": {
                "fields": {
                    "birthdate": {
                        "sd": true
                    },
                    "family_name": {
                        "sd": true
                    }
                }
            },
            "x5Chain": [
                "-----BEGIN CERTIFICATE-----\nMIIBeTCCAR8CFHrWgrGl5KdefSvRQhR+aoqdf48+MAoGCCqGSM49BAMCMBcxFTATBgNVBAMMDE1ET0MgUk9PVCBDQTAgFw0yNTA1MTQxNDA4MDlaGA8yMDc1MDUwMjE0MDgwOVowZTELMAkGA1UEBhMCQVQxDzANBgNVBAgMBlZpZW5uYTEPMA0GA1UEBwwGVmllbm5hMRAwDgYDVQQKDAd3YWx0LmlkMRAwDgYDVQQLDAd3YWx0LmlkMRAwDgYDVQQDDAd3YWx0LmlzMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEG0RINBiF+oQUD3d5DGnegQuXenI29JDaMGoMvioKRBN53d4UazakS2unu8BnsEtxutS2kqRhYBPYk9RAriU3gTAKBggqhkjOPQQDAgNIADBFAiAOMwM7hH7q9Di+mT6qCi4LvB+kH8OxMheIrZ2eRPxtDQIhALHzTxwvN8Udt0Z2Cpo8JBihqacfeXkIxVAO8XkxmXhB\n-----END CERTIFICATE-----"
            ]
        }
    """.trimIndent()
        )
        private val sdJwtVcPresentationRequest = Json.decodeFromString<JsonObject>(
            """
        {
            "request_credentials": [
                {
                    "format": "vc+sd-jwt",
                    "vct": "https://issuer.portal.walt-test.cloud/identity_credential",
                    "input_descriptor": {
                        "id": "some-id-id",
                        "format": {
                            "vc+sd-jwt": {}
                        },
                        "constraints": {
                            "fields": [
                                {
                                    "path": [
                                        "${'$'}.birthdate"
                                    ],
                                    "filter": {
                                        "type": "string",
                                        "pattern": ".*"
                                    }
                                }
                            ],
                            "limit_disclosure": "required"
                        }
                    }
                }
            ],
            "vp_policies": [
                "signature_sd-jwt-vc",
                "presentation-definition"
            ],
            "vc_policies": [
                "not-before",
                "expired"
            ]
        }
    """.trimIndent()
        )

        val uniDegreeOpenBadgePresentationRequest = buildJsonObject {
            put("request_credentials", buildJsonArray {
                addJsonObject {
                    put("format", VCFormat.jwt_vc_json.toJsonElement())
                    put("type", "OpenBadgeCredential".toJsonElement())
                }
                addJsonObject {
                    put("format", VCFormat.jwt_vc_json.toJsonElement())
                    put("type", "OpenBadgeCredential".toJsonElement())
                }
            })
        }

        var openBadgeWithDisclosuresWalletCredentialId: String? = null
        var universityDegreeWithDisclosuresWalletCredentialId: String? = null
        var openBadgeDisclosures: List<String>? = null
        var universityDegreeDisclosures: List<String>? = null


    }

    @Order(0)
    @Test
    fun issueUniDegreeNoDisclosures() = runTest {
        val offerUrl = issuerApi.issueJwtCredential(universityDegreeNoDisclosuresIssuanceRequest)
        val universityDegreeNoDisclosuresWalletCredentialId =
            defaultWalletApi.claimCredential(offerUrl).first().id

        val sessionId = Uuid.random().toString()
        val presentationUrl = verifierApi.verify(universityDegreeNoDisclosurePresentationRequest, sessionId)
            .assertContainsPresentationDefinitionUri()

        defaultWalletApi.usePresentationRequest(
            UsePresentationRequest(
                presentationRequest = presentationUrl,
                selectedCredentials = listOf(universityDegreeNoDisclosuresWalletCredentialId),
            )
        )

        verifierApi.getSession(sessionId).let {
            assertTrue(it.verificationResult!!)
        }

        val simpleViewByDefaultResponse = verifierApi.getPresentedCredentials(sessionId)

        assertEquals(
            actual = simpleViewByDefaultResponse.viewMode,
            expected = PresentedCredentialsViewMode.simple,
        )

        assertEquals(
            actual = simpleViewByDefaultResponse.credentialsByFormat.keys,
            expected = setOf(VCFormat.jwt_vc_json),
        )

        var credentials =
            assertNotNull(simpleViewByDefaultResponse.credentialsByFormat[VCFormat.jwt_vc_json])

        assertEquals(1, credentials.size)

        val jwtVcJsonPresentationSimpleView = assertDoesNotThrow {
            credentials.first() as PresentedJwtVcJsonSimpleViewMode
        }

        val holder = assertNotNull(jwtVcJsonPresentationSimpleView.holder)

        assertEquals(
            expected = defaultWalletApi.getDefaultDid().did,
            actual = holder.jsonPrimitive.content,
        )

        assertEquals(1, jwtVcJsonPresentationSimpleView.verifiableCredentials.size)

        val simpleViewResponse = verifierApi.getPresentedCredentials(sessionId, PresentedCredentialsViewMode.simple)
        assertEquals(
            expected = simpleViewByDefaultResponse,
            actual = simpleViewResponse,
        )

        val verboseViewResponse = verifierApi.getPresentedCredentials(sessionId, PresentedCredentialsViewMode.verbose)
        assertNotEquals(
            illegal = simpleViewResponse,
            actual = verboseViewResponse,
        )

        assertEquals(
            actual = verboseViewResponse.viewMode,
            expected = PresentedCredentialsViewMode.verbose,
        )

        assertEquals(
            actual = verboseViewResponse.credentialsByFormat.keys,
            expected = setOf(VCFormat.jwt_vc_json),
        )

        credentials =
            assertNotNull(verboseViewResponse.credentialsByFormat[VCFormat.jwt_vc_json])

        assertEquals(1, credentials.size)

        val jwtVcJsonPresentationVerboseView = assertDoesNotThrow {
            credentials.first() as PresentedJwtVcJsonVerboseViewMode
        }

        assertEquals(1, jwtVcJsonPresentationVerboseView.verifiableCredentials.size)

    }

    @Order(0)
    @Test
    fun issueUniDegreeWithDisclosures() = runTest {
        val offerUrl = issuerApi.issueJwtCredential(universityDegreeWithDisclosuresIssuanceRequest)
        universityDegreeWithDisclosuresWalletCredentialId =
            defaultWalletApi.claimCredential(offerUrl).first().let {
                assertNotNull(it.disclosures)
                assertNotEquals("", it.disclosures)
                universityDegreeDisclosures = listOf(it.disclosures!!)
                it.id
            }

        val sessionId = Uuid.random().toString()
        val presentationUrl = verifierApi.verify(universityDegreeWithDisclosuresPresentationRequest, sessionId)
            .assertContainsPresentationDefinitionUri()
        defaultWalletApi.usePresentationRequest(
            UsePresentationRequest(
                presentationRequest = presentationUrl,
                selectedCredentials = listOf(universityDegreeWithDisclosuresWalletCredentialId!!),
                disclosures = mapOf(
                    universityDegreeWithDisclosuresWalletCredentialId!! to universityDegreeDisclosures!!
                )
            )
        )

        verifierApi.getSession(sessionId).also {
            assertTrue(it.verificationResult!!)
        }

        val simpleViewByDefaultResponse = verifierApi.getPresentedCredentials(sessionId)

        assertEquals(
            actual = simpleViewByDefaultResponse.viewMode,
            expected = PresentedCredentialsViewMode.simple,
        )

        assertEquals(
            actual = simpleViewByDefaultResponse.credentialsByFormat.keys,
            expected = setOf(VCFormat.jwt_vc_json),
        )

        var credentials =
            assertNotNull(simpleViewByDefaultResponse.credentialsByFormat[VCFormat.jwt_vc_json])

        assertEquals(1, credentials.size)

        val jwtVcJsonPresentationSimpleView = assertDoesNotThrow {
            credentials.first() as PresentedJwtVcJsonSimpleViewMode
        }

        val holder = assertNotNull(jwtVcJsonPresentationSimpleView.holder)

        assertEquals(
            expected = defaultWalletApi.getDefaultDid().did,
            actual = holder.jsonPrimitive.content,
        )

        assertEquals(1, jwtVcJsonPresentationSimpleView.verifiableCredentials.size)

        val simpleViewResponse = verifierApi.getPresentedCredentials(sessionId, PresentedCredentialsViewMode.simple)
        assertEquals(
            expected = simpleViewByDefaultResponse,
            actual = simpleViewResponse,
        )

        val verboseViewResponse = verifierApi.getPresentedCredentials(sessionId, PresentedCredentialsViewMode.verbose)
        assertNotEquals(
            illegal = simpleViewResponse,
            actual = verboseViewResponse,
        )

        assertEquals(
            actual = verboseViewResponse.viewMode,
            expected = PresentedCredentialsViewMode.verbose,
        )

        assertEquals(
            actual = verboseViewResponse.credentialsByFormat.keys,
            expected = setOf(VCFormat.jwt_vc_json),
        )

        credentials =
            assertNotNull(verboseViewResponse.credentialsByFormat[VCFormat.jwt_vc_json])

        assertEquals(1, credentials.size)

        val jwtVcJsonPresentationVerboseView = assertDoesNotThrow {
            credentials.first() as PresentedJwtVcJsonVerboseViewMode
        }

        assertEquals(1, jwtVcJsonPresentationVerboseView.verifiableCredentials.size)

        val verboseCredential = jwtVcJsonPresentationVerboseView.verifiableCredentials.first()

        assertNotEquals(verboseCredential.fullPayload, verboseCredential.undisclosedPayload)

        assertTrue((verboseCredential.undisclosedPayload["vc"] as JsonObject).containsKey("_sd"))

        universityDegreeWithDisclosuresIssuanceRequest.selectiveDisclosure!!.fields.keys.forEach {
            assertContains(
                verboseCredential.fullPayload["vc"] as JsonObject,
                it,
            )
        }

        val disclosures = assertNotNull(verboseCredential.disclosures)

        assertEquals(2, disclosures.size)
    }


    @Order(0)
    @Test
    fun issueOpenBadgeNoDisclosures() = runTest {
        val offerUrl = issuerApi.issueJwtCredential(openBadgeNoDisclosuresIssuanceRequest)
        val openBadgeNoDisclosuresWalletCredentialId =
            defaultWalletApi.claimCredential(offerUrl).first().id

        val sessionId = Uuid.random().toString()
        val presentationUrl = verifierApi.verify(openBadgeNoDisclosurePresentationRequest, sessionId)
            .assertContainsPresentationDefinitionUri()
        defaultWalletApi.usePresentationRequest(
            UsePresentationRequest(
                presentationRequest = presentationUrl,
                selectedCredentials = listOf(openBadgeNoDisclosuresWalletCredentialId),
            )
        )

        verifierApi.getSession(sessionId).also {
            assertTrue(it.verificationResult!!)
        }

        val simpleViewByDefaultResponse = verifierApi.getPresentedCredentials(sessionId)
        assertEquals(
            actual = simpleViewByDefaultResponse.viewMode,
            expected = PresentedCredentialsViewMode.simple,
        )

        assertEquals(
            actual = simpleViewByDefaultResponse.credentialsByFormat.keys,
            expected = setOf(VCFormat.jwt_vc_json),
        )

        var credentials =
            assertNotNull(simpleViewByDefaultResponse.credentialsByFormat[VCFormat.jwt_vc_json])

        assertEquals(1, credentials.size)

        val jwtVcJsonPresentationSimpleView = assertDoesNotThrow {
            credentials.first() as PresentedJwtVcJsonSimpleViewMode
        }

        val holder = assertNotNull(jwtVcJsonPresentationSimpleView.holder)

        assertEquals(
            expected = defaultWalletApi.getDefaultDid().did,
            actual = holder.jsonPrimitive.content,
        )

        assertEquals(1, jwtVcJsonPresentationSimpleView.verifiableCredentials.size)

        val simpleViewResponse = verifierApi.getPresentedCredentials(sessionId, PresentedCredentialsViewMode.simple)
        assertEquals(
            expected = simpleViewByDefaultResponse,
            actual = simpleViewResponse,
        )

        val verboseViewResponse = verifierApi.getPresentedCredentials(sessionId, PresentedCredentialsViewMode.verbose)
        assertNotEquals(
            illegal = simpleViewResponse,
            actual = verboseViewResponse,
        )

        assertEquals(
            actual = verboseViewResponse.viewMode,
            expected = PresentedCredentialsViewMode.verbose,
        )

        assertEquals(
            actual = verboseViewResponse.credentialsByFormat.keys,
            expected = setOf(VCFormat.jwt_vc_json),
        )

        credentials =
            assertNotNull(verboseViewResponse.credentialsByFormat[VCFormat.jwt_vc_json])

        assertEquals(1, credentials.size)

        val jwtVcJsonPresentationVerboseView = assertDoesNotThrow {
            credentials.first() as PresentedJwtVcJsonVerboseViewMode
        }

        assertEquals(1, jwtVcJsonPresentationVerboseView.verifiableCredentials.size)

    }

    @Test
    fun issueSdJwtVc() = runTest {
        val offerUrl = issuerApi.issueSdJwtCredential(sdJwtVcIssuanceRequest)
        lateinit var sdJwtVcDisclosures: List<String>
        val sdJwtVcWalletCredentialId = defaultWalletApi.claimCredential(offerUrl).first().let {
            assertNotNull(it.disclosures)
            assertNotEquals("", it.disclosures)
            sdJwtVcDisclosures = listOf(it.disclosures!!)
            it.id
        }

        val sessionId = Uuid.random().toString()
        val presentationUrl = verifierApi.verify(sdJwtVcPresentationRequest, sessionId)
            .assertContainsPresentationDefinitionUri()

        defaultWalletApi.usePresentationRequest(
            UsePresentationRequest(
                presentationRequest = presentationUrl,
                selectedCredentials = listOf(sdJwtVcWalletCredentialId),
                disclosures = mapOf(
                    sdJwtVcWalletCredentialId to sdJwtVcDisclosures,
                )
            )
        )

        verifierApi.getSession(sessionId).also {
            assertTrue(it.verificationResult!!)
        }

        val simpleViewByDefaultResponse = verifierApi.getPresentedCredentials(sessionId)
        assertEquals(
            actual = simpleViewByDefaultResponse.viewMode,
            expected = PresentedCredentialsViewMode.simple,
        )

        assertEquals(
            actual = simpleViewByDefaultResponse.credentialsByFormat.keys,
            expected = setOf(VCFormat.sd_jwt_vc),
        )

        var credentials =
            assertNotNull(simpleViewByDefaultResponse.credentialsByFormat[VCFormat.sd_jwt_vc])

        assertEquals(1, credentials.size)

        val sdJwtVcPresentationSimpleView = assertDoesNotThrow {
            credentials.first() as PresentedSdJwtVcSimpleViewMode
        }

        assertNotNull(sdJwtVcPresentationSimpleView.keyBinding)

        val simpleViewResponse = verifierApi.getPresentedCredentials(sessionId, PresentedCredentialsViewMode.simple)
        assertEquals(
            expected = simpleViewByDefaultResponse,
            actual = simpleViewResponse,
        )

        val verboseViewResponse = verifierApi.getPresentedCredentials(sessionId, PresentedCredentialsViewMode.verbose)
        assertNotEquals(
            illegal = simpleViewResponse,
            actual = verboseViewResponse,
        )

        assertEquals(
            actual = verboseViewResponse.viewMode,
            expected = PresentedCredentialsViewMode.verbose,
        )

        assertEquals(
            actual = verboseViewResponse.credentialsByFormat.keys,
            expected = setOf(VCFormat.sd_jwt_vc),
        )

        credentials =
            assertNotNull(verboseViewResponse.credentialsByFormat[VCFormat.sd_jwt_vc])

        assertEquals(1, credentials.size)

        val sdJwtVcPresentationVerboseView = assertDoesNotThrow {
            credentials.first() as PresentedSdJwtVcVerboseViewMode
        }

        assertFalse(sdJwtVcPresentationVerboseView.raw.isBlank())
        assertNotNull(sdJwtVcPresentationVerboseView.keyBinding)

        assertNotEquals(
            sdJwtVcPresentationVerboseView.vc.fullPayload,
            sdJwtVcPresentationVerboseView.vc.undisclosedPayload
        )

        assertTrue(sdJwtVcPresentationVerboseView.vc.undisclosedPayload.containsKey("_sd"))

        sdJwtVcIssuanceRequest.selectiveDisclosure!!.fields.keys.forEach {
            assertContains(
                sdJwtVcPresentationVerboseView.vc.fullPayload,
                it,
            )
        }

        val disclosures = assertNotNull(sdJwtVcPresentationVerboseView.vc.disclosures)

        assertEquals(2, disclosures.size)
    }

    @Order(10)
    @Test
    fun issueOpenBadgeWithDisclosures() = runTest {
        val offerUrl = issuerApi.issueJwtCredential(openBadgeWithDisclosuresIssuanceRequest)
        openBadgeWithDisclosuresWalletCredentialId =
            defaultWalletApi.claimCredential(offerUrl).first().let {
                assertNotNull(it.disclosures)
                assertNotEquals("", it.disclosures)
                openBadgeDisclosures = listOf(it.disclosures!!)
                it.id
            }

        val sessionId = Uuid.random().toString()
        val presentationUrl = verifierApi.verify(openBadgeWithDisclosuresPresentationRequest, sessionId)
            .assertContainsPresentationDefinitionUri()

        defaultWalletApi.usePresentationRequest(
            UsePresentationRequest(
                presentationRequest = presentationUrl,
                selectedCredentials = listOf(openBadgeWithDisclosuresWalletCredentialId!!),
                disclosures = mapOf(
                    openBadgeWithDisclosuresWalletCredentialId!! to openBadgeDisclosures!!
                )
            )
        )

        verifierApi.getSession(sessionId).also {
            assertTrue(it.verificationResult!!)
        }

        val simpleViewByDefaultResponse = verifierApi.getPresentedCredentials(sessionId)
        assertEquals(
            actual = simpleViewByDefaultResponse.viewMode,
            expected = PresentedCredentialsViewMode.simple,
        )

        assertEquals(
            actual = simpleViewByDefaultResponse.credentialsByFormat.keys,
            expected = setOf(VCFormat.jwt_vc_json),
        )

        var credentials =
            assertNotNull(simpleViewByDefaultResponse.credentialsByFormat[VCFormat.jwt_vc_json])

        assertEquals(1, credentials.size)

        val jwtVcJsonPresentationSimpleView = assertDoesNotThrow {
            credentials.first() as PresentedJwtVcJsonSimpleViewMode
        }

        val holder = assertNotNull(jwtVcJsonPresentationSimpleView.holder)

        assertEquals(
            expected = defaultWalletApi.getDefaultDid().did,
            actual = holder.jsonPrimitive.content,
        )

        assertEquals(1, jwtVcJsonPresentationSimpleView.verifiableCredentials.size)

        val simpleViewResponse = verifierApi.getPresentedCredentials(sessionId, PresentedCredentialsViewMode.simple)
        assertEquals(
            expected = simpleViewByDefaultResponse,
            actual = simpleViewResponse,
        )

        val verboseViewResponse = verifierApi.getPresentedCredentials(sessionId, PresentedCredentialsViewMode.verbose)
        assertNotEquals(
            illegal = simpleViewResponse,
            actual = verboseViewResponse,
        )

        assertEquals(
            actual = verboseViewResponse.viewMode,
            expected = PresentedCredentialsViewMode.verbose,
        )

        assertEquals(
            actual = verboseViewResponse.credentialsByFormat.keys,
            expected = setOf(VCFormat.jwt_vc_json),
        )

        credentials =
            assertNotNull(verboseViewResponse.credentialsByFormat[VCFormat.jwt_vc_json])

        assertEquals(1, credentials.size)

        val jwtVcJsonPresentationVerboseView = assertDoesNotThrow {
            credentials.first() as PresentedJwtVcJsonVerboseViewMode
        }

        assertEquals(1, jwtVcJsonPresentationVerboseView.verifiableCredentials.size)

        val verboseCredential = jwtVcJsonPresentationVerboseView.verifiableCredentials.first()

        assertNotEquals(verboseCredential.fullPayload, verboseCredential.undisclosedPayload)

        assertTrue((verboseCredential.undisclosedPayload["vc"] as JsonObject).containsKey("_sd"))

        openBadgeWithDisclosuresIssuanceRequest.selectiveDisclosure!!.fields.keys.forEach {
            assertContains(
                verboseCredential.fullPayload["vc"] as JsonObject,
                it,
            )
        }

        val disclosures = assertNotNull(verboseCredential.disclosures)

        assertEquals(2, disclosures.size)

    }


    @Disabled("A wallet with other key type is needed")
    @Test
    fun issueMdl() = runTest {
        val mDocWallet = environment.getMdocWalletApi()
        val keys = mDocWallet.listKeys()
        assertNotNull(keys)
        val offerUrl = issuerApi.issueMdocCredential(MdocDocs.mdlBaseIssuanceExample)
        val mDLWalletCredentialId = mDocWallet.claimCredential(offerUrl).first().id
        val sessionId = Uuid.random().toString()
        val presentationUrl = verifierApi.verify(
            VerifierApiExamples.mDLRequiredFieldsExample,
            sessionId,
            ResponseMode.direct_post_jwt
        ).assertContainsPresentationDefinitionUri()

        mDocWallet.usePresentationRequest(
            UsePresentationRequest(
                presentationRequest = presentationUrl,
                selectedCredentials = listOf(mDLWalletCredentialId),
            )
        )

        verifierApi.getSession(sessionId).also {
            assertTrue(it.verificationResult!!)
        }

        val simpleViewByDefaultResponse = verifierApi.getPresentedCredentials(sessionId)
        assertEquals(
            actual = simpleViewByDefaultResponse.viewMode,
            expected = PresentedCredentialsViewMode.simple,
        )

        assertEquals(
            actual = simpleViewByDefaultResponse.credentialsByFormat.keys,
            expected = setOf(VCFormat.mso_mdoc),
        )

        var credentials =
            assertNotNull(simpleViewByDefaultResponse.credentialsByFormat[VCFormat.mso_mdoc])

        assertEquals(1, credentials.size)

        val msoMdocPresentationSimpleView = assertDoesNotThrow {
            credentials.first() as PresentedMsoMdocSimpleViewMode
        }

        assertEquals(
            expected = "1.0",
            actual = msoMdocPresentationSimpleView.version,
        )

        assertEquals(
            expected = 0,
            actual = msoMdocPresentationSimpleView.status,
        )

        val simpleViewResponse = verifierApi.getPresentedCredentials(sessionId, PresentedCredentialsViewMode.simple)
        assertEquals(
            expected = simpleViewByDefaultResponse,
            actual = simpleViewResponse,
        )

        val verboseViewResponse = verifierApi.getPresentedCredentials(sessionId, PresentedCredentialsViewMode.verbose)
        assertNotEquals(
            illegal = simpleViewResponse,
            actual = verboseViewResponse,
        )

        assertEquals(
            actual = verboseViewResponse.viewMode,
            expected = PresentedCredentialsViewMode.verbose,
        )

        assertEquals(
            actual = verboseViewResponse.credentialsByFormat.keys,
            expected = setOf(VCFormat.mso_mdoc),
        )

        credentials =
            assertNotNull(verboseViewResponse.credentialsByFormat[VCFormat.mso_mdoc])

        assertEquals(1, credentials.size)

        val msoMdocPresentationVerboseView = assertDoesNotThrow {
            credentials.first() as PresentedMsoMdocVerboseViewMode
        }

        assertTrue(msoMdocPresentationVerboseView.raw.isNotBlank())

        assertEquals(
            expected = "1.0",
            actual = msoMdocPresentationVerboseView.version,
        )

        assertEquals(
            expected = 0,
            actual = msoMdocPresentationVerboseView.status,
        )

        assertEquals(1, msoMdocPresentationVerboseView.documents.size)

        val mDoc = msoMdocPresentationVerboseView.documents[0]

        assertEquals(
            expected = "org.iso.18013.5.1.mDL",
            actual = mDoc.docType
        )

        assertNull(mDoc.errors)

        assertEquals(1, mDoc.issuerSigned.nameSpaces.size)

        assertEquals(
            actual = mDoc.issuerSigned.nameSpaces.keys,
            expected = setOf("org.iso.18013.5.1"),
        )
    }

    @Test
    fun queryPresentedCredentialsBeforeVpTokenSubmission() = runTest {
        val sessionId = Uuid.random().toString()
        verifierApi.verify(universityDegreeWithDisclosuresPresentationRequest, sessionId)
            .assertContainsPresentationDefinitionUri()

        verifierApi.getPresentedCredentialsRaw(sessionId)
            .expectFailure()

        verifierApi.getPresentedCredentialsRaw(sessionId, PresentedCredentialsViewMode.simple)
            .expectFailure()

        verifierApi.getPresentedCredentialsRaw(sessionId, PresentedCredentialsViewMode.verbose)
            .expectFailure()
    }

    @Test
    fun queryPresentedCredentialsAfterInvalidVpTokenSubmission() = runTest {
        val sessionId = Uuid.random().toString()
        verifierApi.verify(universityDegreeWithDisclosuresPresentationRequest, sessionId)
            .assertContainsPresentationDefinitionUri()

        val dummyEcKey = KeyManager.createKey(
            generationRequest = KeyGenerationRequest(
                backend = "jwk",
                keyType = KeyType.secp256r1,
            )
        )

        val dummyPresentationSubmissionString =
            """{"id":"X0zlmZ3BuJNS","definition_id":"X0zlmZ3BuJNS","descriptor_map":[{"id":"UniversityDegreeCredential","format":"jwt_vp","path":"${'$'}","path_nested":{"format":"jwt_vc","path":"${'$'}.vp.verifiableCredential[0]"}}]}"""

        val dummyVpToken = dummyEcKey.signJws(
            plaintext = Json.encodeToString(buildJsonObject {
                put("kati", "allo".toJsonElement())
            }).toByteArray(),
        )

        val failure = verifierApi.client.submitForm(
            url = "/openid4vc/verify/${sessionId}",
            formParameters = Parameters.build {
                append("vp_token", dummyVpToken)
                append("presentation_submission", dummyPresentationSubmissionString)
                append("state", sessionId)
            }
        ).expectFailure()
        assertNotNull(failure)

        verifierApi.getSession(sessionId).also {
            assertFalse(it.verificationResult!!)
        }

        verifierApi.getPresentedCredentialsRaw(sessionId)
            .expectFailure()

        verifierApi.getPresentedCredentialsRaw(sessionId, PresentedCredentialsViewMode.simple)
            .expectFailure()

        verifierApi.getPresentedCredentialsRaw(sessionId, PresentedCredentialsViewMode.verbose)
            .expectFailure()
    }

    @Order(100)
    @Test
    fun presentUniDegreeOpenBadgeWithDisclosures() = runTest {
        assertNotNull(openBadgeWithDisclosuresWalletCredentialId, "Test Order !!")
        assertNotNull(openBadgeDisclosures, "Test Order !!")
        assertNotNull(universityDegreeDisclosures, "Test Order !!")
        val sessionId = Uuid.random().toString()
        val presentationUrl = verifierApi.verify(uniDegreeOpenBadgePresentationRequest, sessionId)
            .assertContainsPresentationDefinitionUri()

        defaultWalletApi.usePresentationRequest(
            UsePresentationRequest(
                presentationRequest = presentationUrl,
                selectedCredentials = listOf(
                    openBadgeWithDisclosuresWalletCredentialId!!,
                    universityDegreeWithDisclosuresWalletCredentialId!!,
                ),
                disclosures = mapOf(
                    openBadgeWithDisclosuresWalletCredentialId!! to openBadgeDisclosures!!,
                    universityDegreeWithDisclosuresWalletCredentialId!! to universityDegreeDisclosures!!,
                )
            )
        )

        verifierApi.getSession(sessionId).also {
            assertTrue(it.verificationResult!!)
        }

        val simpleViewByDefaultResponse = verifierApi.getPresentedCredentials(sessionId)
        assertEquals(
            actual = simpleViewByDefaultResponse.viewMode,
            expected = PresentedCredentialsViewMode.simple,
        )

        assertEquals(
            actual = simpleViewByDefaultResponse.credentialsByFormat.keys,
            expected = setOf(VCFormat.jwt_vc_json),
        )

        var credentials =
            assertNotNull(simpleViewByDefaultResponse.credentialsByFormat[VCFormat.jwt_vc_json])

        assertEquals(1, credentials.size)

        val jwtVcJsonPresentationSimpleView = assertDoesNotThrow {
            credentials.first() as PresentedJwtVcJsonSimpleViewMode
        }

        val holder = assertNotNull(jwtVcJsonPresentationSimpleView.holder)

        assertEquals(
            expected = defaultWalletApi.getDefaultDid().did,
            actual = holder.jsonPrimitive.content,
        )

        assertEquals(2, jwtVcJsonPresentationSimpleView.verifiableCredentials.size)

        val simpleViewResponse = verifierApi.getPresentedCredentials(sessionId, PresentedCredentialsViewMode.simple)
        assertEquals(
            expected = simpleViewByDefaultResponse,
            actual = simpleViewResponse,
        )

        val verboseViewResponse = verifierApi.getPresentedCredentials(sessionId, PresentedCredentialsViewMode.verbose)
        assertNotEquals(
            illegal = simpleViewResponse,
            actual = verboseViewResponse,
        )

        assertEquals(
            actual = verboseViewResponse.viewMode,
            expected = PresentedCredentialsViewMode.verbose,
        )

        assertEquals(
            actual = verboseViewResponse.credentialsByFormat.keys,
            expected = setOf(VCFormat.jwt_vc_json),
        )

        credentials =
            assertNotNull(verboseViewResponse.credentialsByFormat[VCFormat.jwt_vc_json])

        assertEquals(1, credentials.size)

        val jwtVcJsonPresentationVerboseView = assertDoesNotThrow {
            credentials.first() as PresentedJwtVcJsonVerboseViewMode
        }

        assertEquals(2, jwtVcJsonPresentationVerboseView.verifiableCredentials.size)

    }
}
