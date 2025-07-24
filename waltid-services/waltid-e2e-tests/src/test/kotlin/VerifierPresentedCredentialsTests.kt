@file:OptIn(ExperimentalUuidApi::class)

import id.walt.commons.testing.E2ETest
import id.walt.commons.testing.utils.ServiceTestUtils
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.did.dids.DidService
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.issuer.issuance.openapi.issuerapi.MdocDocs
import id.walt.oid4vc.data.ResponseMode
import id.walt.sdjwt.SDField
import id.walt.sdjwt.SDMap
import id.walt.verifier.oidc.PresentationSessionInfo
import id.walt.verifier.oidc.models.presentedcredentials.*
import id.walt.verifier.openapi.VerifierApiExamples
import id.walt.w3c.utils.VCFormat
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.web.controllers.exchange.UsePresentationRequest
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertNull
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class VerifierPresentedCredentialsTests(val e2e: E2ETest) {

    private val TEST_SUITE = "Verifier Presented Credentials Test Suite"

    private lateinit var mDocWallet: MDocPreparedWallet

    private val client = e2e.testHttpClient()

    private val issuerKey = runBlocking {
        KeyManager.createKey(
            generationRequest = KeyGenerationRequest(
                keyType = KeyType.secp256r1,
            )
        )
    }

    private val issuerKeyForRequest = buildJsonObject {
        put("type", "jwk".toJsonElement())
        put("jwk", runBlocking {
            issuerKey.exportJWKObject()
        })
    }

    private val issuerDid = runBlocking {
        DidService.registerByKey(
            method = "key",
            key = issuerKey,
        ).did
    }

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
        issuerKey = issuerKeyForRequest,
        issuerDid = issuerDid,
    )


    private lateinit var universityDegreeNoDisclosuresWalletCredentialId: String
    private val universityDegreeNoDisclosurePresentationRequest = buildJsonObject {
        put("request_credentials", buildJsonArray {
            addJsonObject {
                put("format", VCFormat.jwt_vc_json.toJsonElement())
                put("type", "UniversityDegreeCredential".toJsonElement())
            }
        })
    }

    private val openBadgeNoDisclosuresIssuanceRequest = Json.decodeFromString<IssuanceRequest>(
        string = ServiceTestUtils.loadResource("issuance/openbadgecredential-issuance-request.json")
    ).copy(
        issuerKey = issuerKeyForRequest,
        issuerDid = issuerDid,
    )
    private lateinit var openBadgeNoDisclosuresWalletCredentialId: String
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
    private lateinit var universityDegreeWithDisclosuresWalletCredentialId: String
    private lateinit var universityDegreeDisclosures: List<String>
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
    private lateinit var openBadgeWithDisclosuresWalletCredentialId: String
    private lateinit var openBadgeDisclosures: List<String>
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
    private lateinit var sdJwtVcWalletCredentialId: String
    private lateinit var sdJwtVcDisclosures: List<String>
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

    private lateinit var mDLWalletCredentialId: String

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

    private suspend fun setupTestSuite() {
        mDocWallet = MDocPreparedWallet(e2e).createSetupWallet()
        issueUniDegreeNoDisclosures()
        issueUniDegreeWithDisclosures()
        issueOpenBadgeNoDisclosures()
        issueSdJwtVc()
        issueMdl()
        issueOpenBadgeWithDisclosures()
    }

    private suspend fun issueUniDegreeNoDisclosures() =
        e2e.test(
            name = "${TEST_SUITE}: Setup step #6: Issue university degree credential with no disclosures to wallet"
        ) {
            val offerUrl = client.post("/openid4vc/jwt/issue") {
                setBody(universityDegreeNoDisclosuresIssuanceRequest)
            }.expectSuccess().bodyAsText()

            universityDegreeNoDisclosuresWalletCredentialId =
                mDocWallet.walletClient.post("/wallet-api/wallet/${mDocWallet.walletId}/exchange/useOfferRequest") {
                    setBody(offerUrl)
                }.expectSuccess().body<List<WalletCredential>>().first().id
        }

    private suspend fun issueUniDegreeWithDisclosures() =
        e2e.test(
            name = "${TEST_SUITE}: Setup step #7: Issue university degree credential with two dummy disclosures to wallet"
        ) {
            val offerUrl = client.post("/openid4vc/jwt/issue") {
                setBody(universityDegreeWithDisclosuresIssuanceRequest)
            }.expectSuccess().bodyAsText()

            universityDegreeWithDisclosuresWalletCredentialId =
                mDocWallet.walletClient.post("/wallet-api/wallet/${mDocWallet.walletId}/exchange/useOfferRequest") {
                    setBody(offerUrl)
                }.expectSuccess().body<List<WalletCredential>>().first().let {
                    assertNotNull(it.disclosures)
                    assertNotEquals("", it.disclosures)
                    universityDegreeDisclosures = listOf(it.disclosures!!)
                    it.id
                }
        }

    private suspend fun issueOpenBadgeNoDisclosures() =
        e2e.test(
            name = "${TEST_SUITE}: Setup step #8: Issue open badge credential with no disclosures to wallet"
        ) {
            val offerUrl = client.post("/openid4vc/jwt/issue") {
                setBody(openBadgeNoDisclosuresIssuanceRequest)
            }.expectSuccess().bodyAsText()

            openBadgeNoDisclosuresWalletCredentialId =
                mDocWallet.walletClient.post("/wallet-api/wallet/${mDocWallet.walletId}/exchange/useOfferRequest") {
                    setBody(offerUrl)
                }.expectSuccess().body<List<WalletCredential>>().first().id
        }

    private suspend fun issueSdJwtVc() =
        e2e.test(
            name = "${TEST_SUITE}: Setup step #9: Issue sd jwt vc to wallet"
        ) {
            val offerUrl = client.post("/openid4vc/sdjwt/issue") {
                setBody(sdJwtVcIssuanceRequest)
            }.expectSuccess().bodyAsText()

            sdJwtVcWalletCredentialId =
                mDocWallet.walletClient.post("/wallet-api/wallet/${mDocWallet.walletId}/exchange/useOfferRequest") {
                    setBody(offerUrl)
                }.expectSuccess().body<List<WalletCredential>>().first().let {
                    assertNotNull(it.disclosures)
                    assertNotEquals("", it.disclosures)
                    sdJwtVcDisclosures = listOf(it.disclosures!!)
                    it.id
                }
        }

    private suspend fun issueMdl() =
        e2e.test(
            name = "${TEST_SUITE}: Setup step #10: Issue mdl to wallet"
        ) {
            val offerUrl = client.post("/openid4vc/mdoc/issue") {
                setBody(MdocDocs.mdlBaseIssuanceExample)
            }.expectSuccess().bodyAsText()

            mDLWalletCredentialId =
                mDocWallet.walletClient.post("/wallet-api/wallet/${mDocWallet.walletId}/exchange/useOfferRequest") {
                    setBody(offerUrl)
                }.expectSuccess().body<List<WalletCredential>>().first().id
        }

    private suspend fun issueOpenBadgeWithDisclosures() =
        e2e.test(
            name = "${TEST_SUITE}: Setup step #11: Issue open badge credential with two dummy disclosures to wallet"
        ) {
            val offerUrl = client.post("/openid4vc/jwt/issue") {
                setBody(openBadgeWithDisclosuresIssuanceRequest)
            }.expectSuccess().bodyAsText()

            openBadgeWithDisclosuresWalletCredentialId =
                mDocWallet.walletClient.post("/wallet-api/wallet/${mDocWallet.walletId}/exchange/useOfferRequest") {
                    setBody(offerUrl)
                }.expectSuccess().body<List<WalletCredential>>().first().let {
                    assertNotNull(it.disclosures)
                    assertNotEquals("", it.disclosures)
                    openBadgeDisclosures = listOf(it.disclosures!!)
                    it.id
                }
        }

    private suspend fun presentUniDegreeNoDisclosures() =
        e2e.test(
            name = "${TEST_SUITE}: Presentation of university degree credential with no disclosures"
        ) {
            val sessionId = Uuid.random()
            val presentationUrl = client.post("/openid4vc/verify") {
                headers {
                    append("stateId", sessionId.toString())
                }
                setBody(universityDegreeNoDisclosurePresentationRequest)
            }.expectSuccess().bodyAsText()


            mDocWallet.walletClient.post("/wallet-api/wallet/${mDocWallet.walletId}/exchange/usePresentationRequest") {
                setBody(
                    UsePresentationRequest(
                        presentationRequest = presentationUrl,
                        selectedCredentials = listOf(universityDegreeNoDisclosuresWalletCredentialId),
                    )
                )
            }.expectSuccess()

            client.get("/openid4vc/session/${sessionId}")
                .expectSuccess().body<PresentationSessionInfo>().let {
                    assertTrue(it.verificationResult!!)
                }

            val simpleViewByDefaultResponse = client.get("/openid4vc/session/${sessionId}/presented-credentials")
                .expectSuccess().body<PresentationSessionPresentedCredentials>()

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

            assert(credentials.size == 1)

            val jwtVcJsonPresentationSimpleView = assertDoesNotThrow {
                credentials.first() as PresentedJwtVcJsonSimpleViewMode
            }

            val holder = assertNotNull(jwtVcJsonPresentationSimpleView.holder)

            assertEquals(
                expected = mDocWallet.did,
                actual = holder.jsonPrimitive.content,
            )

            assert(jwtVcJsonPresentationSimpleView.verifiableCredentials.size == 1)

            val simpleViewResponse =
                client.get("/openid4vc/session/${sessionId}/presented-credentials") {
                    url {
                        parameters.append("viewMode", PresentedCredentialsViewMode.simple.name)
                    }
                }.expectSuccess().body<PresentationSessionPresentedCredentials>()

            assertEquals(
                expected = simpleViewByDefaultResponse,
                actual = simpleViewResponse,
            )

            val verboseViewResponse =
                client.get("/openid4vc/session/${sessionId}/presented-credentials") {
                    url {
                        parameters.append("viewMode", PresentedCredentialsViewMode.verbose.name)
                    }
                }.expectSuccess().body<PresentationSessionPresentedCredentials>()

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

            assert(credentials.size == 1)

            val jwtVcJsonPresentationVerboseView = assertDoesNotThrow {
                credentials.first() as PresentedJwtVcJsonVerboseViewMode
            }

            assert(jwtVcJsonPresentationVerboseView.verifiableCredentials.size == 1)

        }

    private suspend fun presentOpenBadgeNoDisclosures() =
        e2e.test(
            name = "${TEST_SUITE}: Presentation of open badge credential with no disclosures"
        ) {
            val sessionId = Uuid.random().toString()
            val presentationUrl = client.post("/openid4vc/verify") {
                headers {
                    append("stateId", sessionId)
                }
                setBody(openBadgeNoDisclosurePresentationRequest)
            }.expectSuccess().bodyAsText()


            mDocWallet.walletClient.post("/wallet-api/wallet/${mDocWallet.walletId}/exchange/usePresentationRequest") {
                setBody(
                    UsePresentationRequest(
                        presentationRequest = presentationUrl,
                        selectedCredentials = listOf(openBadgeNoDisclosuresWalletCredentialId),
                    )
                )
            }.expectSuccess()

            client.get("/openid4vc/session/${sessionId}")
                .expectSuccess().body<PresentationSessionInfo>().let {
                    assertTrue(it.verificationResult!!)
                }

            val simpleViewByDefaultResponse = client.get("/openid4vc/session/${sessionId}/presented-credentials")
                .expectSuccess().body<PresentationSessionPresentedCredentials>()

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

            assert(credentials.size == 1)

            val jwtVcJsonPresentationSimpleView = assertDoesNotThrow {
                credentials.first() as PresentedJwtVcJsonSimpleViewMode
            }

            val holder = assertNotNull(jwtVcJsonPresentationSimpleView.holder)

            assertEquals(
                expected = mDocWallet.did,
                actual = holder.jsonPrimitive.content,
            )

            assert(jwtVcJsonPresentationSimpleView.verifiableCredentials.size == 1)

            val simpleViewResponse =
                client.get("/openid4vc/session/${sessionId}/presented-credentials") {
                    url {
                        parameters.append("viewMode", PresentedCredentialsViewMode.simple.name)
                    }
                }.expectSuccess().body<PresentationSessionPresentedCredentials>()

            assertEquals(
                expected = simpleViewByDefaultResponse,
                actual = simpleViewResponse,
            )

            val verboseViewResponse =
                client.get("/openid4vc/session/${sessionId}/presented-credentials") {
                    url {
                        parameters.append("viewMode", PresentedCredentialsViewMode.verbose.name)
                    }
                }.expectSuccess().body<PresentationSessionPresentedCredentials>()

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

            assert(credentials.size == 1)

            val jwtVcJsonPresentationVerboseView = assertDoesNotThrow {
                credentials.first() as PresentedJwtVcJsonVerboseViewMode
            }

            assert(jwtVcJsonPresentationVerboseView.verifiableCredentials.size == 1)

        }

    private suspend fun presentSdJwtVc() =
        e2e.test(
            name = "${TEST_SUITE}: Presentation of sd jwt vc"
        ) {
            val sessionId = Uuid.random().toString()
            val presentationUrl = client.post("/openid4vc/verify") {
                headers {
                    append("stateId", sessionId)
                }
                setBody(sdJwtVcPresentationRequest)
            }.expectSuccess().bodyAsText()


            mDocWallet.walletClient.post("/wallet-api/wallet/${mDocWallet.walletId}/exchange/usePresentationRequest") {
                setBody(
                    UsePresentationRequest(
                        presentationRequest = presentationUrl,
                        selectedCredentials = listOf(sdJwtVcWalletCredentialId),
                        disclosures = mapOf(
                            sdJwtVcWalletCredentialId to sdJwtVcDisclosures,
                        )
                    )
                )
            }.expectSuccess()

            client.get("/openid4vc/session/${sessionId}")
                .expectSuccess().body<PresentationSessionInfo>().let {
                    assertTrue(it.verificationResult!!)
                }

            val simpleViewByDefaultResponse = client.get("/openid4vc/session/${sessionId}/presented-credentials")
                .expectSuccess().body<PresentationSessionPresentedCredentials>()

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

            assert(credentials.size == 1)

            val sdJwtVcPresentationSimpleView = assertDoesNotThrow {
                credentials.first() as PresentedSdJwtVcSimpleViewMode
            }

            assertNotNull(sdJwtVcPresentationSimpleView.keyBinding)

            val simpleViewResponse =
                client.get("/openid4vc/session/${sessionId}/presented-credentials") {
                    url {
                        parameters.append("viewMode", PresentedCredentialsViewMode.simple.name)
                    }
                }.expectSuccess().body<PresentationSessionPresentedCredentials>()

            assertEquals(
                expected = simpleViewByDefaultResponse,
                actual = simpleViewResponse,
            )

            val verboseViewResponse =
                client.get("/openid4vc/session/${sessionId}/presented-credentials") {
                    url {
                        parameters.append("viewMode", PresentedCredentialsViewMode.verbose.name)
                    }
                }.expectSuccess().body<PresentationSessionPresentedCredentials>()

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

            assert(credentials.size == 1)

            val sdJwtVcPresentationVerboseView = assertDoesNotThrow {
                credentials.first() as PresentedSdJwtVcVerboseViewMode
            }

            assert(sdJwtVcPresentationVerboseView.raw.isNotBlank())
            assertNotNull(sdJwtVcPresentationVerboseView.keyBinding)

            assert(sdJwtVcPresentationVerboseView.vc.fullPayload != sdJwtVcPresentationVerboseView.vc.undisclosedPayload)

            assert(sdJwtVcPresentationVerboseView.vc.undisclosedPayload.containsKey("_sd"))

            sdJwtVcIssuanceRequest.selectiveDisclosure!!.fields.keys.forEach {
                assertContains(
                    sdJwtVcPresentationVerboseView.vc.fullPayload,
                    it,
                )
            }

            val disclosures = assertNotNull(sdJwtVcPresentationVerboseView.vc.disclosures)

            assert(disclosures.size == 2)

        }

    private suspend fun presentMdl() =
        e2e.test(
            name = "${TEST_SUITE}: Presentation of mDL"
        ) {
            val sessionId = Uuid.random().toString()
            val presentationUrl = client.post("/openid4vc/verify") {
                headers {
                    append("stateId", sessionId)
                    append("responseMode", ResponseMode.direct_post_jwt.toString())
                }
                setBody(VerifierApiExamples.mDLRequiredFieldsExample)
            }.expectSuccess().bodyAsText()


            mDocWallet.walletClient.post("/wallet-api/wallet/${mDocWallet.walletId}/exchange/usePresentationRequest") {
                setBody(
                    UsePresentationRequest(
                        presentationRequest = presentationUrl,
                        selectedCredentials = listOf(mDLWalletCredentialId),
                    )
                )
            }.expectSuccess()

            client.get("/openid4vc/session/${sessionId}")
                .expectSuccess().body<PresentationSessionInfo>().let {
                    assertTrue(it.verificationResult!!)
                }

            val simpleViewByDefaultResponse = client.get("/openid4vc/session/${sessionId}/presented-credentials")
                .expectSuccess().body<PresentationSessionPresentedCredentials>()

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

            assert(credentials.size == 1)

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

            val simpleViewResponse =
                client.get("/openid4vc/session/${sessionId}/presented-credentials") {
                    url {
                        parameters.append("viewMode", PresentedCredentialsViewMode.simple.name)
                    }
                }.expectSuccess().body<PresentationSessionPresentedCredentials>()

            assertEquals(
                expected = simpleViewByDefaultResponse,
                actual = simpleViewResponse,
            )

            val verboseViewResponse =
                client.get("/openid4vc/session/${sessionId}/presented-credentials") {
                    url {
                        parameters.append("viewMode", PresentedCredentialsViewMode.verbose.name)
                    }
                }.expectSuccess().body<PresentationSessionPresentedCredentials>()

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

            assert(credentials.size == 1)

            val msoMdocPresentationVerboseView = assertDoesNotThrow {
                credentials.first() as PresentedMsoMdocVerboseViewMode
            }

            assert(msoMdocPresentationVerboseView.raw.isNotBlank())

            assertEquals(
                expected = "1.0",
                actual = msoMdocPresentationVerboseView.version,
            )

            assertEquals(
                expected = 0,
                actual = msoMdocPresentationVerboseView.status,
            )

            assert(msoMdocPresentationVerboseView.documents.size == 1)

            val mDoc = msoMdocPresentationVerboseView.documents[0]

            assertEquals(
                expected = "org.iso.18013.5.1.mDL",
                actual = mDoc.docType
            )

            assertNull(mDoc.errors)

            assert(mDoc.issuerSigned.nameSpaces.size == 1)

            assertEquals(
                actual = mDoc.issuerSigned.nameSpaces.keys,
                expected = setOf("org.iso.18013.5.1"),
            )

        }

    private suspend fun presentOpenBadgeWithDisclosures() =
        e2e.test(
            name = "${TEST_SUITE}: Presentation of open badge credential with disclosures"
        ) {
            val sessionId = Uuid.random().toString()
            val presentationUrl = client.post("/openid4vc/verify") {
                headers {
                    append("stateId", sessionId)
                }
                setBody(openBadgeWithDisclosuresPresentationRequest)
            }.expectSuccess().bodyAsText()


            mDocWallet.walletClient.post("/wallet-api/wallet/${mDocWallet.walletId}/exchange/usePresentationRequest") {
                setBody(
                    UsePresentationRequest(
                        presentationRequest = presentationUrl,
                        selectedCredentials = listOf(openBadgeWithDisclosuresWalletCredentialId),
                        disclosures = mapOf(
                            openBadgeWithDisclosuresWalletCredentialId to openBadgeDisclosures
                        )
                    )
                )
            }.expectSuccess()

            client.get("/openid4vc/session/${sessionId}")
                .expectSuccess().body<PresentationSessionInfo>().let {
                    assertTrue(it.verificationResult!!)
                }

            val simpleViewByDefaultResponse = client.get("/openid4vc/session/${sessionId}/presented-credentials")
                .expectSuccess().body<PresentationSessionPresentedCredentials>()

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

            assert(credentials.size == 1)

            val jwtVcJsonPresentationSimpleView = assertDoesNotThrow {
                credentials.first() as PresentedJwtVcJsonSimpleViewMode
            }

            val holder = assertNotNull(jwtVcJsonPresentationSimpleView.holder)

            assertEquals(
                expected = mDocWallet.did,
                actual = holder.jsonPrimitive.content,
            )

            assert(jwtVcJsonPresentationSimpleView.verifiableCredentials.size == 1)

            val simpleViewResponse =
                client.get("/openid4vc/session/${sessionId}/presented-credentials") {
                    url {
                        parameters.append("viewMode", PresentedCredentialsViewMode.simple.name)
                    }
                }.expectSuccess().body<PresentationSessionPresentedCredentials>()

            assertEquals(
                expected = simpleViewByDefaultResponse,
                actual = simpleViewResponse,
            )

            val verboseViewResponse =
                client.get("/openid4vc/session/${sessionId}/presented-credentials") {
                    url {
                        parameters.append("viewMode", PresentedCredentialsViewMode.verbose.name)
                    }
                }.expectSuccess().body<PresentationSessionPresentedCredentials>()

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

            assert(credentials.size == 1)

            val jwtVcJsonPresentationVerboseView = assertDoesNotThrow {
                credentials.first() as PresentedJwtVcJsonVerboseViewMode
            }

            assert(jwtVcJsonPresentationVerboseView.verifiableCredentials.size == 1)

            val verboseCredential = jwtVcJsonPresentationVerboseView.verifiableCredentials.first()

            assert(verboseCredential.fullPayload != verboseCredential.undisclosedPayload)

            assert((verboseCredential.undisclosedPayload["vc"] as JsonObject).containsKey("_sd"))

            openBadgeWithDisclosuresIssuanceRequest.selectiveDisclosure!!.fields.keys.forEach {
                assertContains(
                    verboseCredential.fullPayload["vc"] as JsonObject,
                    it,
                )
            }

            val disclosures = assertNotNull(verboseCredential.disclosures)

            assert(disclosures.size == 2)

        }

    private suspend fun presentUniversityDegreeWithDisclosures() =
        e2e.test(
            name = "${TEST_SUITE}: Presentation of university degree credential with disclosures"
        ) {
            val sessionId = Uuid.random().toString()
            val presentationUrl = client.post("/openid4vc/verify") {
                headers {
                    append("stateId", sessionId)
                }
                setBody(universityDegreeWithDisclosuresPresentationRequest)
            }.expectSuccess().bodyAsText()


            mDocWallet.walletClient.post("/wallet-api/wallet/${mDocWallet.walletId}/exchange/usePresentationRequest") {
                setBody(
                    UsePresentationRequest(
                        presentationRequest = presentationUrl,
                        selectedCredentials = listOf(universityDegreeWithDisclosuresWalletCredentialId),
                        disclosures = mapOf(
                            universityDegreeWithDisclosuresWalletCredentialId to universityDegreeDisclosures
                        )
                    )
                )
            }.expectSuccess()

            client.get("/openid4vc/session/${sessionId}")
                .expectSuccess().body<PresentationSessionInfo>().let {
                    assertTrue(it.verificationResult!!)
                }

            val simpleViewByDefaultResponse = client.get("/openid4vc/session/${sessionId}/presented-credentials")
                .expectSuccess().body<PresentationSessionPresentedCredentials>()

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

            assert(credentials.size == 1)

            val jwtVcJsonPresentationSimpleView = assertDoesNotThrow {
                credentials.first() as PresentedJwtVcJsonSimpleViewMode
            }

            val holder = assertNotNull(jwtVcJsonPresentationSimpleView.holder)

            assertEquals(
                expected = mDocWallet.did,
                actual = holder.jsonPrimitive.content,
            )

            assert(jwtVcJsonPresentationSimpleView.verifiableCredentials.size == 1)

            val simpleViewResponse =
                client.get("/openid4vc/session/${sessionId}/presented-credentials") {
                    url {
                        parameters.append("viewMode", PresentedCredentialsViewMode.simple.name)
                    }
                }.expectSuccess().body<PresentationSessionPresentedCredentials>()

            assertEquals(
                expected = simpleViewByDefaultResponse,
                actual = simpleViewResponse,
            )

            val verboseViewResponse =
                client.get("/openid4vc/session/${sessionId}/presented-credentials") {
                    url {
                        parameters.append("viewMode", PresentedCredentialsViewMode.verbose.name)
                    }
                }.expectSuccess().body<PresentationSessionPresentedCredentials>()

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

            assert(credentials.size == 1)

            val jwtVcJsonPresentationVerboseView = assertDoesNotThrow {
                credentials.first() as PresentedJwtVcJsonVerboseViewMode
            }

            assert(jwtVcJsonPresentationVerboseView.verifiableCredentials.size == 1)

            val verboseCredential = jwtVcJsonPresentationVerboseView.verifiableCredentials.first()

            assert(verboseCredential.fullPayload != verboseCredential.undisclosedPayload)

            assert((verboseCredential.undisclosedPayload["vc"] as JsonObject).containsKey("_sd"))

            universityDegreeWithDisclosuresIssuanceRequest.selectiveDisclosure!!.fields.keys.forEach {
                assertContains(
                    verboseCredential.fullPayload["vc"] as JsonObject,
                    it,
                )
            }

            val disclosures = assertNotNull(verboseCredential.disclosures)

            assert(disclosures.size == 2)

        }

    private suspend fun queryPresentedCredentialsBeforeVpTokenSubmission() =
        e2e.test(
            name = "${TEST_SUITE}: Attempt to query presented credentials for session before submission of vp_token"
        ) {

            val sessionId = Uuid.random().toString()
            client.post("/openid4vc/verify") {
                headers {
                    append("stateId", sessionId)
                }
                setBody(universityDegreeWithDisclosuresPresentationRequest)
            }.expectSuccess().bodyAsText()

            client.get("/openid4vc/session/${sessionId}/presented-credentials")
                .expectFailure()

            client.get("/openid4vc/session/${sessionId}/presented-credentials") {
                url {
                    parameters.append("viewMode", PresentedCredentialsViewMode.simple.name)
                }
            }.expectFailure()

            client.get("/openid4vc/session/${sessionId}/presented-credentials") {
                url {
                    parameters.append("viewMode", PresentedCredentialsViewMode.verbose.name)
                }
            }.expectFailure()

        }

    private suspend fun queryPresentedCredentialsAfterInvalidVpTokenSubmission() =
        e2e.test(
            name = "${TEST_SUITE}: Attempt to query presented credentials for session with invalid vp_token submitted"
        ) {

            val sessionId = Uuid.random().toString()
            client.post("/openid4vc/verify") {
                headers {
                    append("stateId", sessionId)
                }
                setBody(universityDegreeWithDisclosuresPresentationRequest)
            }.expectSuccess().bodyAsText()

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

            client.submitForm(
                url = "/openid4vc/verify/${sessionId}",
                formParameters = Parameters.build {
                    append("vp_token", dummyVpToken)
                    append("presentation_submission", dummyPresentationSubmissionString)
                    append("state", sessionId)
                }
            ).expectFailure()

            client.get("/openid4vc/session/${sessionId}")
                .expectSuccess().body<PresentationSessionInfo>().let {
                    assertFalse(it.verificationResult!!)
                }

            client.get("/openid4vc/session/${sessionId}/presented-credentials")
                .expectFailure()

            client.get("/openid4vc/session/${sessionId}/presented-credentials") {
                url {
                    parameters.append("viewMode", PresentedCredentialsViewMode.simple.name)
                }
            }.expectFailure()

            client.get("/openid4vc/session/${sessionId}/presented-credentials") {
                url {
                    parameters.append("viewMode", PresentedCredentialsViewMode.verbose.name)
                }
            }.expectFailure()

        }

    private suspend fun presentUniDegreeOpenBadgeWithDisclosures() =
        e2e.test(
            name = "${TEST_SUITE}: Presentation of university degree  and open badge credentials, both with disclosures"
        ) {
            val sessionId = Uuid.random().toString()
            val presentationUrl = client.post("/openid4vc/verify") {
                headers {
                    append("stateId", sessionId)
                }
                setBody(uniDegreeOpenBadgePresentationRequest)
            }.expectSuccess().bodyAsText()


            mDocWallet.walletClient.post("/wallet-api/wallet/${mDocWallet.walletId}/exchange/usePresentationRequest") {
                setBody(
                    UsePresentationRequest(
                        presentationRequest = presentationUrl,
                        selectedCredentials = listOf(
                            openBadgeWithDisclosuresWalletCredentialId,
                            universityDegreeWithDisclosuresWalletCredentialId,
                        ),
                        disclosures = mapOf(
                            openBadgeWithDisclosuresWalletCredentialId to openBadgeDisclosures,
                            universityDegreeWithDisclosuresWalletCredentialId to universityDegreeDisclosures,
                        )
                    )
                )
            }.expectSuccess()

            client.get("/openid4vc/session/${sessionId}")
                .expectSuccess().body<PresentationSessionInfo>().let {
                    assertTrue(it.verificationResult!!)
                }

            val simpleViewByDefaultResponse = client.get("/openid4vc/session/${sessionId}/presented-credentials")
                .expectSuccess().body<PresentationSessionPresentedCredentials>()

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

            assert(credentials.size == 1)

            val jwtVcJsonPresentationSimpleView = assertDoesNotThrow {
                credentials.first() as PresentedJwtVcJsonSimpleViewMode
            }

            val holder = assertNotNull(jwtVcJsonPresentationSimpleView.holder)

            assertEquals(
                expected = mDocWallet.did,
                actual = holder.jsonPrimitive.content,
            )

            assert(jwtVcJsonPresentationSimpleView.verifiableCredentials.size == 2)

            val simpleViewResponse =
                client.get("/openid4vc/session/${sessionId}/presented-credentials") {
                    url {
                        parameters.append("viewMode", PresentedCredentialsViewMode.simple.name)
                    }
                }.expectSuccess().body<PresentationSessionPresentedCredentials>()

            assertEquals(
                expected = simpleViewByDefaultResponse,
                actual = simpleViewResponse,
            )

            val verboseViewResponse =
                client.get("/openid4vc/session/${sessionId}/presented-credentials") {
                    url {
                        parameters.append("viewMode", PresentedCredentialsViewMode.verbose.name)
                    }
                }.expectSuccess().body<PresentationSessionPresentedCredentials>()

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

            assert(credentials.size == 1)

            val jwtVcJsonPresentationVerboseView = assertDoesNotThrow {
                credentials.first() as PresentedJwtVcJsonVerboseViewMode
            }

            assert(jwtVcJsonPresentationVerboseView.verifiableCredentials.size == 2)

        }

    suspend fun runTests() {
        setupTestSuite()
        presentUniDegreeNoDisclosures()
        presentOpenBadgeNoDisclosures()
        presentSdJwtVc()
        presentMdl()
        presentOpenBadgeWithDisclosures()
        presentUniversityDegreeWithDisclosures()
        queryPresentedCredentialsBeforeVpTokenSubmission()
        queryPresentedCredentialsAfterInvalidVpTokenSubmission()
        presentUniDegreeOpenBadgeWithDisclosures()
    }
}
