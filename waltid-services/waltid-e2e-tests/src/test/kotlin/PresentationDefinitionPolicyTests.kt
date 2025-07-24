@file:OptIn(ExperimentalUuidApi::class)

import id.walt.commons.testing.E2ETest
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.UuidUtils.randomUUID
import id.walt.crypto.utils.UuidUtils.randomUUIDString
import id.walt.verifier.oidc.PresentationSessionInfo
import id.walt.webwallet.db.models.AccountWalletListing
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.db.models.WalletDid
import id.walt.webwallet.service.keys.SingleKeyResponse
import id.walt.webwallet.web.controllers.exchange.UsePresentationRequest
import id.walt.webwallet.web.model.AccountRequest
import id.walt.webwallet.web.model.EmailAccountRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.util.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)

class PresentationDefinitionPolicyTests(private val e2e: E2ETest) {

    private val email = "${randomUUID()}@mail.com"
    private val password = randomUUIDString()


    private lateinit var walletUuid: Uuid
    private lateinit var walletId: String
    private lateinit var did: String
    private lateinit var keyId: String

    private var client: HttpClient = WaltidServicesE2ETests.testHttpClient()

    private suspend fun createWalletAccount() {
        client.post("/wallet-api/auth/register") {
            setBody(
                EmailAccountRequest(
                    email = email,
                    password = password,
                ) as AccountRequest
            )
        }.expectSuccess()
    }

    private suspend fun loginWallet() {
        client.post("/wallet-api/auth/login") {
            setBody(
                EmailAccountRequest(
                    email = email,
                    password = password,
                ) as AccountRequest
            )
        }.expectSuccess()
            .body<JsonObject>()
            .let {
                client = WaltidServicesE2ETests.testHttpClient(token = it["token"]!!.jsonPrimitive.content)
            }
    }

    private suspend fun getAccountWalletId() {
        client.get("/wallet-api/wallet/accounts/wallets").expectSuccess().apply {
            val listing = body<AccountWalletListing>()
            assert(listing.wallets.isNotEmpty()) { "No wallets available!" }
            walletId = listing.wallets.first().id.toString()
            walletUuid = Uuid.parse(walletId)
        }
    }

    private suspend fun deleteWalletCredentials() {
        client.get("/wallet-api/wallet/$walletId/credentials")
            .expectSuccess()
            .body<List<WalletCredential>>()
            .forEach {
                client.delete("/wallet-api/wallet/$walletId/credentials/${it.id}?permanent=true").expectSuccess()
            }
    }

    private suspend fun deleteWalletKeys() {
        client.get("/wallet-api/wallet/$walletId/keys")
            .expectSuccess()
            .body<List<SingleKeyResponse>>()
            .forEach {
                client.delete("/wallet-api/wallet/$walletId/keys/${it.keyId.id}")
            }
    }

    private suspend fun deleteWalletDids() {
        client.get("/wallet-api/wallet/$walletId/dids")
            .expectSuccess()
            .body<List<WalletDid>>()
            .forEach {
                client.delete("/wallet-api/wallet/$walletId/dids/${it.did}")
            }
    }

    private suspend fun clearAllWalletData() {
        deleteWalletKeys()
        deleteWalletDids()
        deleteWalletCredentials()
    }

    private suspend fun createKey() {
        client.post("/wallet-api/wallet/$walletId/keys/generate") {
            setBody(
                KeyGenerationRequest(
                    backend = "jwk",
                    keyType = KeyType.secp256r1,
                )
            )
        }.expectSuccess().bodyAsText().let {
            keyId = it
        }
    }

    private suspend fun createDid() {
        client.post("/wallet-api/wallet/$walletId/dids/create/jwk?keyId=${keyId}")
            .expectSuccess()
            .bodyAsText()
            .let {
                did = it
            }
    }

    private suspend fun getWalletCredentials() = client.get("/wallet-api/wallet/$walletId/credentials")
        .expectSuccess()
        .body<List<WalletCredential>>()


    private suspend fun setupTestCases() {
        createWalletAccount()
        loginWallet()
        getAccountWalletId()
        clearAllWalletData()
        createKey()
        createDid()
    }

    suspend fun runTests() {
        setupTestCases()

        runTestScenario(
            description = "Presentation Definition Policy Scenario - JWT VC JSON University Degree, " +
                    "no selectively disclosable claims, " +
                    "vc prefix in JSON paths",
            setup = {
                issueCredentialsToWallet(
                    issuanceRequests = listOf(
                        IssuanceRequests.universityDegree,
                    )
                )
            },
            evaluate = {
                evaluatePresentationVerificationResult(
                    presentationRequest = PresentationRequests.universityDegreeW3CVcTypeCorrectJsonPath,
                    expectedVerificationResult = true,
                    provideDisclosures = false,
                )
            },
            cleanup = {
                deleteWalletCredentials()
            },
        )

        runTestScenario(
            description = "Presentation Definition Policy Scenario - JWT VC JSON University Degree, " +
                    "no selectively disclosable claims, " +
                    "NO vc prefix in JSON paths",
            setup = {
                issueCredentialsToWallet(
                    issuanceRequests = listOf(
                        IssuanceRequests.universityDegree,
                    )
                )
            },
            evaluate = {
                evaluatePresentationVerificationResult(
                    presentationRequest = PresentationRequests.universityDegreeW3CVcTypeInvalidJsonPath,
                    expectedVerificationResult = false,
                    provideDisclosures = false,
                )
            },
            cleanup = {
                deleteWalletCredentials()
            },
        )

        runTestScenario(
            description = "Presentation Definition Policy Scenario - JWT VC JSON University Degree, " +
                    "presentation request requiring university degree and PND91Credential (in order), " +
                    "wallet only has university degree, " +
                    "wallet cannot satisfy request and verification should fail",
            setup = {
                issueCredentialsToWallet(
                    issuanceRequests = listOf(
                        IssuanceRequests.universityDegree,
                    )
                )
            },
            evaluate = {
                evaluatePresentationVerificationResult(
                    presentationRequest = PresentationRequests.universityDegreePnd91CredentialsInOrder,
                    expectedVerificationResult = false,
                    provideDisclosures = false,
                )
            },
            cleanup = {
                deleteWalletCredentials()
            },
        )

        runTestScenario(
            description = "Presentation Definition Policy Scenario - JWT VC JSON University Degree, " +
                    "presentation request requiring PND91Credential and university degree (in order), " +
                    "wallet only has university degree, " +
                    "wallet cannot satisfy request and verification should fail",
            setup = {
                issueCredentialsToWallet(
                    issuanceRequests = listOf(
                        IssuanceRequests.universityDegree,
                    )
                )
            },
            evaluate = {
                evaluatePresentationVerificationResult(
                    presentationRequest = PresentationRequests.pnd91UniversityDegreeCredentialsInOrder,
                    expectedVerificationResult = false,
                    provideDisclosures = false,
                )
            },
            cleanup = {
                deleteWalletCredentials()
            },
        )

        runTestScenario(
            description = "Presentation Definition Policy Scenario - JWT VC JSON University Degree, " +
                    "with credentialSubject.degree.type selectively disclosable, " +
                    "wallet provides disclosures, " +
                    "verification should succeed",
            setup = {
                issueCredentialsToWallet(
                    issuanceRequests = listOf(
                        IssuanceRequests.universityDegreeW3CVcTypeSd,
                    )
                )
            },
            evaluate = {
                evaluatePresentationVerificationResult(
                    presentationRequest = PresentationRequests.universityDegreeCredentialSubjectDegreeTypeCorrectJsonPath,
                    expectedVerificationResult = true,
                    provideDisclosures = true,
                )
            },
            cleanup = {
                deleteWalletCredentials()
            },
        )

        runTestScenario(
            description = "Presentation Definition Policy Scenario - JWT VC JSON University Degree, " +
                    "with credentialSubject.degree.type selectively disclosable, " +
                    "wallet DOES NOT provide disclosures, " +
                    "verification should fail",
            setup = {
                issueCredentialsToWallet(
                    issuanceRequests = listOf(
                        IssuanceRequests.universityDegreeW3CVcTypeSd,
                    )
                )
            },
            evaluate = {
                evaluatePresentationVerificationResult(
                    presentationRequest = PresentationRequests.universityDegreeCredentialSubjectDegreeTypeCorrectJsonPath,
                    expectedVerificationResult = false,
                    provideDisclosures = false,
                )
            },
            cleanup = {
                deleteWalletCredentials()
            },
        )

        runTestScenario(
            description = "Presentation Definition Policy Scenario - JWT VC JSON University Degree, " +
                    "with credentialSubject.degree.type selectively disclosable, " +
                    "wallet provide disclosures, " +
                    "value requested for presentation does not match one contained in subject degree type, " +
                    "verification should fail",
            setup = {
                issueCredentialsToWallet(
                    issuanceRequests = listOf(
                        IssuanceRequests.universityDegreeW3CVcTypeSd,
                    )
                )
            },
            evaluate = {
                evaluatePresentationVerificationResult(
                    presentationRequest = PresentationRequests.getUniversityDegreeWithSubjectDegreeTypeValue("potato"),
                    expectedVerificationResult = false,
                    provideDisclosures = true,
                )
            },
            cleanup = {
                deleteWalletCredentials()
            },
        )

        runTestScenario(
            description = "Presentation Definition Policy Scenario - JWT VC JSON University Degree, " +
                    "with credentialSubject.degree.type selectively disclosable, " +
                    "wallet provide disclosures, " +
                    "any value can satisfy the presentation request, " +
                    "verification should succeed",
            setup = {
                issueCredentialsToWallet(
                    issuanceRequests = listOf(
                        IssuanceRequests.universityDegreeW3CVcTypeSd,
                    )
                )
            },
            evaluate = {
                evaluatePresentationVerificationResult(
                    presentationRequest = PresentationRequests.getUniversityDegreeWithSubjectDegreeTypeValue(".*"),
                    expectedVerificationResult = true,
                    provideDisclosures = true,
                )
            },
            cleanup = {
                deleteWalletCredentials()
            },
        )

        runTestScenario(
            description = "Presentation Definition Policy Scenario - JWT VC JSON University Degree, " +
                    "with credentialSubject.degree.type selectively disclosable, " +
                    "wallet DOES NOT provide disclosures, " +
                    "any value can satisfy the presentation request, " +
                    "verification should fail",
            setup = {
                issueCredentialsToWallet(
                    issuanceRequests = listOf(
                        IssuanceRequests.universityDegreeW3CVcTypeSd,
                    )
                )
            },
            evaluate = {
                evaluatePresentationVerificationResult(
                    presentationRequest = PresentationRequests.getUniversityDegreeWithSubjectDegreeTypeValue(".*"),
                    expectedVerificationResult = false,
                    provideDisclosures = false,
                )
            },
            cleanup = {
                deleteWalletCredentials()
            },
        )

        runTestScenario(
            description = "Presentation Definition Policy Scenario - IETF SD-JWT VC Identity Credential, " +
                    "presentation request with two input descriptors, " +
                    "second input descriptor requires is_over_65 to be true, " +
                    "verification should succeed",
            setup = {
                issueCredentialsToWallet(
                    issuanceRequests = listOf(
                        IssuanceRequests.identityCredential,
                    )
                )
            },
            evaluate = {
                evaluatePresentationVerificationResult(
                    presentationRequest = PresentationRequests.getIdentityCredentialWithTwoInputDescriptors(),
                    expectedVerificationResult = true,
                    provideDisclosures = true,
                )
            },
            cleanup = {
                deleteWalletCredentials()
            },
        )

        runTestScenario(
            description = "Presentation Definition Policy Scenario - IETF SD-JWT VC Identity Credential, " +
                    "presentation request with two input descriptors, " +
                    "second input descriptor requires is_over_65 to be false, " +
                    "verification should fail",
            setup = {
                issueCredentialsToWallet(
                    issuanceRequests = listOf(
                        IssuanceRequests.identityCredential,
                    )
                )
            },
            evaluate = {
                evaluatePresentationVerificationResult(
                    presentationRequest = PresentationRequests.getIdentityCredentialWithTwoInputDescriptors(false),
                    expectedVerificationResult = false,
                    provideDisclosures = true,
                )
            },
            cleanup = {
                deleteWalletCredentials()
            },
        )
// TODO: Include test in the scope of WAL-842
//        runTestScenario(
//            description = "Presentation Definition Policy Scenario - UniversityDegree and PDA1 credentials, " +
//                    "presentation request with two input descriptors, " +
//                    "one for each credential, " +
//                    "verification should succeed",
//            setup = {
//                issueCredentialsToWallet(
//                    issuanceRequests = listOf(
//                        IssuanceRequests.universityDegreeW3CVcTypeSd,
//                        IssuanceRequests.pda1Credential,
//                    )
//                )
//            },
//            evaluate = {
//                evaluatePresentationVerificationResult(
//                    presentationRequest = PresentationRequests.getUniversityDegreePda1ToSeparateInputDescriptors(),
//                    expectedVerificationResult = true,
//                    provideDisclosures = true,
//                )
//            },
//            cleanup = {
//                deleteWalletCredentials()
//            },
//        )

        runTestScenario(
            description = "Presentation Definition Policy Scenario - UniversityDegree and PDA1 credentials, " +
                    "presentation request with two input descriptors, " +
                    "one for each credential, " +
                    "value for sex field of PDA1 cannot be satisfied by the wallet, " +
                    "verification should fail",
            setup = {
                issueCredentialsToWallet(
                    issuanceRequests = listOf(
                        IssuanceRequests.universityDegreeW3CVcTypeSd,
                        IssuanceRequests.pda1Credential,
                    )
                )
            },
            evaluate = {
                evaluatePresentationVerificationResult(
                    presentationRequest = PresentationRequests.getUniversityDegreePda1ToSeparateInputDescriptors("XXX"),
                    expectedVerificationResult = false,
                    provideDisclosures = true,
                )
            },
            cleanup = {
                deleteWalletCredentials()
            },
        )

    }

    private suspend fun issueCredentialsToWallet(
        issuanceRequests: List<String>,
    ) {
        issuanceRequests.forEach { request ->
            client.post("/openid4vc/jwt/issue") {
                setBody(request)
            }.expectSuccess()
                .body<String>()
                .let { offerUrl ->
                    client.post("/wallet-api/wallet/$walletId/exchange/useOfferRequest") {
                        setBody(offerUrl)
                    }.expectSuccess()
                        .body<List<WalletCredential>>()
                        .let {
                            assert(it.isNotEmpty())
                        }
                }
        }
    }

    private suspend fun evaluatePresentationVerificationResult(
        presentationRequest: String,
        expectedVerificationResult: Boolean,
        provideDisclosures: Boolean
    ) {
        val presentationUrl = client.post("/openid4vc/verify") {
            setBody(presentationRequest)
        }.expectSuccess().body<String>()
        val presentationSessionId = Url(presentationUrl).parameters.getOrFail("state")

        val walletCredentials = getWalletCredentials()
        val disclosures = if (provideDisclosures) {
            walletCredentials
                .filter {
                    it.disclosures != null
                }.associate {
                    it.id to listOf(it.disclosures!!)
                }

        } else null
        client.post("/wallet-api/wallet/$walletId/exchange/usePresentationRequest") {
            setBody(
                UsePresentationRequest(
                    did = did,
                    presentationRequest = presentationUrl,
                    selectedCredentials = walletCredentials.map { it.id },
                    disclosures = disclosures,
                )
            )
        }

        client
            .get("/openid4vc/session/$presentationSessionId")
            .expectSuccess()
            .body<PresentationSessionInfo>()
            .let {
                assert(it.verificationResult == expectedVerificationResult)
            }
    }

    private suspend fun runTestScenario(
        description: String,
        setup: suspend () -> Unit,
        evaluate: suspend () -> Unit,
        cleanup: suspend () -> Unit,
    ) {
        setup()
        e2e.test(
            name = description,
        ) {
            evaluate()
        }
        cleanup()
    }

    private object PresentationRequests {

        val universityDegreeW3CVcTypeCorrectJsonPath = """
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
              "format": "jwt_vc_json",
              "input_descriptor": {
                "id": "some-id",              
                "constraints": {
                  "fields": [
                    {
                      "path": [
                        "${'$'}.vc.type"
                      ],
                      "filter": {
                        "type": "string",
                        "pattern": "UniversityDegree"
                      }
                    }
                  ]
                }
              }
            }
          ]
        }
    """.trimIndent()

        val universityDegreeW3CVcTypeInvalidJsonPath = """
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
              "format": "jwt_vc_json",
              "input_descriptor": {
                "id": "some-id",
                "constraints": {
                  "fields": [
                    {
                      "path": [
                        "${'$'}.type"
                      ],
                      "filter": {
                        "type": "string",
                        "pattern": "UniversityDegree"
                      }
                    }
                  ]
                }
              }
            }
          ]
        }
    """.trimIndent()

        val universityDegreePnd91CredentialsInOrder = """
        {
           "vp_policies": [
              "presentation-definition"
           ],
           "request_credentials": [
              {
                 "format": "jwt_vc_json",
                 "type": "UniversityDegree"
              },
              {
                 "format": "jwt_vc_json",
                 "type": "PND91Credential"
              }
           ]
        }
    """.trimIndent()

        val pnd91UniversityDegreeCredentialsInOrder = """
        {
           "vp_policies": [
              "presentation-definition"
           ],
           "request_credentials": [
              {
                 "format": "jwt_vc_json",
                 "type": "UniversityDegree"
              },
              {
                 "format": "jwt_vc_json",
                 "type": "PND91Credential"
              }
           ]
        }
    """.trimIndent()

        val universityDegreeCredentialSubjectDegreeTypeCorrectJsonPath = """
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
              "format": "jwt_vc_json",
              "input_descriptor": {
                "id": "some-id",              
                "constraints": {
                  "fields": [
                    {
                      "path": [
                        "${'$'}.vc.type"
                      ],
                      "filter": {
                        "type": "string",
                        "pattern": "UniversityDegree"
                      }
                    },                  
                    {
                      "path": [
                        "${'$'}.vc.credentialSubject.degree.type"
                      ],
                      "filter": {
                        "type": "string",
                        "pattern": "BachelorDegree"
                      }
                    }
                  ]
                }
              }
            }
          ]
        }
    """.trimIndent()

        fun getUniversityDegreeWithSubjectDegreeTypeValue(subjectDegreeType: String = "BachelorDegree") =
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
              "format": "jwt_vc_json",
              "input_descriptor": {
                "id": "some-id",              
                "constraints": {
                  "fields": [
                    {
                      "path": [
                        "${'$'}.vc.type"
                      ],
                      "filter": {
                        "type": "string",
                        "pattern": "UniversityDegree"
                      }
                    },                  
                    {
                      "path": [
                        "${'$'}.vc.credentialSubject.degree.type"
                      ],
                      "filter": {
                        "type": "string",
                        "pattern": "$subjectDegreeType"
                      }
                    }
                  ]
                }
              }
            }
          ]
        }            
        """.trimIndent()

        fun getIdentityCredentialWithTwoInputDescriptors(
            isOver65: Boolean = true,
        ) = """
            {
              "request_credentials": [
                {
                  "format": "vc+sd-jwt",
                  "input_descriptor": {
                    "id": "some-id",
                    "format": {
                      "vc+sd-jwt": {}
                    },
                    "constraints": {
                      "fields": [
                        {
                          "path": [
                            "${'$'}.vct"
                          ],
                          "filter": {
                            "type": "string",
                            "pattern": "http://localhost:22222/identity_credential"
                          }
                        }
                      ]
                    }
                  }
                },
                {
                  "format": "vc+sd-jwt",
                  "input_descriptor": {
                    "id": "some-id-1",
                    "format": {
                      "vc+sd-jwt": {}
                    },
                    "constraints": {
                      "fields": [
                        {
                          "path": [
                            "${'$'}.is_over_65"
                          ],
                          "filter": {
                            "const": $isOver65
                          }
                        }
                      ]
                    }
                  }
                }
              ],
              "vp_policies": [
                "presentation-definition"
              ]
            }
        """.trimIndent()

        fun getUniversityDegreePda1ToSeparateInputDescriptors(
            sex: String = "01",
        ) = """
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
                  "format": "jwt_vc_json",
                  "input_descriptor": {
                    "id": "some-id",
                    "constraints": {
                      "fields": [
                        {
                          "path": [
                            "${'$'}.vc.credentialSubject.degree.type"
                          ],
                          "filter": {
                            "type": "string",
                            "pattern": "BachelorDegree"
                          }
                        }
                      ]
                    }
                  }
                },
                {
                  "format": "jwt_vc_json",
                  "input_descriptor": {
                    "id": "some-id-1",
                    "constraints": {
                      "fields": [
                        {
                          "path": [
                            "${'$'}.vc.type"
                          ],
                          "filter": {
                            "type": "string",
                            "pattern": "VerifiablePortableDocumentA1"
                          }
                        },
                        {
                          "path": [
                            "${'$'}.vc.credentialSubject.section1.sex"
                          ],
                          "filter": {
                            "type": "string",
                            "pattern": "$sex"
                          }
                        }
                      ]
                    }
                  }
                }
              ]
            }
        """.trimIndent()

    }

    object IssuanceRequests {

        val universityDegree = """
        {
          "issuerKey": {
            "type": "jwk",
            "jwk": {
              "kty": "OKP",
              "d": "mDhpwaH6JYSrD2Bq7Cs-pzmsjlLj4EOhxyI-9DM1mFI",
              "crv": "Ed25519",
              "kid": "Vzx7l5fh56F3Pf9aR3DECU5BwfrY6ZJe05aiWYWzan8",
              "x": "T3T4-u1Xz3vAV2JwPNxWfs4pik_JLiArz_WTCvrCFUM"
            }
          },
          "credentialConfigurationId": "UniversityDegree_jwt_vc_json",
          "credentialData": {
            "@context": [
              "https://www.w3.org/2018/credentials/v1",
              "https://www.w3.org/2018/credentials/examples/v1"
            ],
            "id": "http://example.gov/credentials/3732",
            "type": [
              "VerifiableCredential",
              "UniversityDegreeCredential"
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
          "issuerDid": "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp"
        }
    """.trimIndent()

        val universityDegreeW3CVcTypeSd = """
        {
          "issuerKey": {
            "type": "jwk",
            "jwk": {
              "kty": "OKP",
              "d": "mDhpwaH6JYSrD2Bq7Cs-pzmsjlLj4EOhxyI-9DM1mFI",
              "crv": "Ed25519",
              "kid": "Vzx7l5fh56F3Pf9aR3DECU5BwfrY6ZJe05aiWYWzan8",
              "x": "T3T4-u1Xz3vAV2JwPNxWfs4pik_JLiArz_WTCvrCFUM"
            }
          },
          "issuerDid": "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp",
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
          "selectiveDisclosure": {
            "fields": {
              "credentialSubject": {
                "sd": false,
                "children": {
                  "fields": {
                    "degree": {
                      "sd": false,
                      "children": {
                        "fields": {
                          "type": {
                            "sd": true
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
    """.trimIndent()

        val pda1Credential = """
            {
              "issuerKey": {
                "type": "jwk",
                "jwk": {
                  "kty": "EC",
                  "x": "SgfOvOk1TL5yiXhK5Nq7OwKfn_RUkDizlIhAf8qd2wE",
                  "y": "u_y5JZOsw3SrnNPydzJkoaiqb8raSdCNE_nPovt1fNI",
                  "crv": "P-256",
                  "d": "UqSi2MbJmPczfRmwRDeOJrdivoEy-qk4OEDjFwJYlUI"
                }
              },
              "credentialConfigurationId": "VerifiablePortableDocumentA1_jwt_vc",
              "credentialData": {
                "@context": [
                  "https://www.w3.org/2018/credentials/v1"
                ],
                "id": "https://www.w3.org/2018/credentials/v1",
                "type": [
                  "VerifiableCredential",
                  "VerifiableAttestation",
                  "VerifiablePortableDocumentA1"
                ],
                "issuer": "did:ebsi:zf39qHTXaLrr6iy3tQhT3UZ",
                "issuanceDate": "2020-03-10T04:24:12Z",
                "credentialSubject": {
                  "id": "did:key:z2dmzD81cgPx8Vki7JbuuMmFYrWPgYoytykUZ3eyqht1j9KbrvQgsKodq2xnfBMYGk99qtunHHQuvvi35kRvbH9SDnue2ZNJqcnaU7yAxeKqEqDX4qFzeKYCj6rdbFnTsf4c8QjFXcgGYS21Db9d2FhHxw9ZEnqt9KPgLsLbQHVAmNNZoz",
                  "section1": {
                    "personalIdentificationNumber": "1",
                    "sex": "01",
                    "surname": "Savvaidis",
                    "forenames": "Charalampos",
                    "dateBirth": "1985-08-15",
                    "nationalities": [
                      "BE"
                    ],
                    "stateOfResidenceAddress": {
                      "streetNo": "sss, nnn ",
                      "postCode": "ppp",
                      "town": "ccc",
                      "countryCode": "BE"
                    },
                    "stateOfStayAddress": {
                      "streetNo": "sss, nnn ",
                      "postCode": "ppp",
                      "town": "ccc",
                      "countryCode": "BE"
                    }
                  },
                  "section2": {
                    "memberStateWhichLegislationApplies": "DE",
                    "startingDate": "2022-10-09",
                    "endingDate": "2022-10-29",
                    "certificateForDurationActivity": true,
                    "determinationProvisional": false,
                    "transitionRulesApplyAsEC8832004": false
                  },
                  "section3": {
                    "postedEmployedPerson": false,
                    "employedTwoOrMoreStates": false,
                    "postedSelfEmployedPerson": true,
                    "selfEmployedTwoOrMoreStates": true,
                    "civilServant": true,
                    "contractStaff": false,
                    "mariner": false,
                    "employedAndSelfEmployed": false,
                    "civilAndEmployedSelfEmployed": true,
                    "flightCrewMember": false,
                    "exception": false,
                    "exceptionDescription": "",
                    "workingInStateUnder21": false
                  },
                  "section4": {
                    "employee": false,
                    "selfEmployedActivity": true,
                    "nameBusinessName": "1",
                    "registeredAddress": {
                      "streetNo": "1, 1 1",
                      "postCode": "1",
                      "town": "1",
                      "countryCode": "DE"
                    }
                  },
                  "section5": {
                    "noFixedAddress": true
                  },
                  "section6": {
                    "name": "National Institute for the Social Security of the Self-employed (NISSE)",
                    "address": {
                      "streetNo": "Quai de Willebroeck 35",
                      "postCode": "1000",
                      "town": "Bruxelles",
                      "countryCode": "BE"
                    },
                    "institutionID": "NSSIE/INASTI/RSVZ",
                    "officeFaxNo": "",
                    "officePhoneNo": "0800 12 018",
                    "email": "info@rsvz-inasti.fgov.be",
                    "date": "2022-10-28",
                    "signature": "Official signature"
                  }
                }
              },
              "mapping": {
                "id": "<uuid>",
                "issuer": "<issuerDid>",
                "credentialSubject": {
                  "id": "<subjectDid>"
                },
                "issuanceDate": "<timestamp-ebsi>",
                "issued": "<timestamp-ebsi>",
                "validFrom": "<timestamp-ebsi>",
                "expirationDate": "<timestamp-ebsi-in:365d>",
                "credentialSchema": {
                  "id": "https://api-conformance.ebsi.eu/trusted-schemas-registry/v3/schemas/z5qB8tydkn3Xk3VXb15SJ9dAWW6wky1YEoVdGzudWzhcW",
                  "type": "FullJsonSchemaValidator2021"
                }
              },
              "selectiveDisclosure": {
                "fields": {
                  "credentialSubject": {
                    "sd": false,
                    "children": {
                      "fields": {
                        "section1": {
                          "sd": false,
                          "children": {
                            "fields": {
                              "personalIdentificationNumber": {
                                "sd": true
                              },
                              "sex": {
                                "sd": true
                              },
                              "surname": {
                                "sd": true
                              },
                              "forenames": {
                                "sd": true
                              },
                              "dateBirth": {
                                "sd": true
                              },
                              "nationalities": {
                                "sd": true
                              },
                              "stateOfResidenceAddress": {
                                "sd": true
                              },
                              "stateOfStayAddress": {
                                "sd": true
                              }
                            },
                            "decoyMode": "NONE",
                            "decoys": 0
                          }
                        },
                        "section3": {
                          "sd": false,
                          "children": {
                            "fields": {
                              "postedEmployedPerson": {
                                "sd": true
                              },
                              "employedTwoOrMoreStates": {
                                "sd": true
                              },
                              "postedSelfEmployedPerson": {
                                "sd": true
                              },
                              "selfEmployedTwoOrMoreStates": {
                                "sd": true
                              },
                              "civilServant": {
                                "sd": true
                              },
                              "contractStaff": {
                                "sd": true
                              },
                              "mariner": {
                                "sd": true
                              },
                              "employedAndSelfEmployed": {
                                "sd": true
                              },
                              "civilAndEmployedSelfEmployed": {
                                "sd": true
                              },
                              "flightCrewMember": {
                                "sd": true
                              },
                              "exception": {
                                "sd": true
                              },
                              "exceptionDescription": {
                                "sd": true
                              },
                              "workingInStateUnder21": {
                                "sd": true
                              }
                            },
                            "decoyMode": "NONE",
                            "decoys": 0
                          }
                        },
                        "section4": {
                          "sd": false,
                          "children": {
                            "fields": {
                              "employee": {
                                "sd": true
                              },
                              "selfEmployedActivity": {
                                "sd": true
                              },
                              "nameBusinessName": {
                                "sd": true
                              },
                              "registeredAddress": {
                                "sd": true
                              }
                            },
                            "decoyMode": "NONE",
                            "decoys": 0
                          }
                        },
                        "section5": {
                          "sd": false,
                          "children": {
                            "fields": {
                              "noFixedAddress": {
                                "sd": true
                              }
                            },
                            "decoyMode": "NONE",
                            "decoys": 0
                          }
                        },
                        "section6": {
                          "sd": false,
                          "children": {
                            "fields": {
                              "name": {
                                "sd": true
                              },
                              "address": {
                                "sd": true
                              },
                              "institutionID": {
                                "sd": true
                              },
                              "officeFaxNo": {
                                "sd": true
                              },
                              "officePhoneNo": {
                                "sd": true
                              },
                              "email": {
                                "sd": true
                              },
                              "date": {
                                "sd": true
                              },
                              "signature": {
                                "sd": true
                              }
                            },
                            "decoyMode": "NONE",
                            "decoys": 0
                          }
                        }
                      },
                      "decoyMode": "NONE",
                      "decoys": 0
                    }
                  }
                },
                "decoyMode": "NONE",
                "decoys": 0
              },
              "authenticationMethod": "PRE_AUTHORIZED",
              "issuerDid": "did:ebsi:zf39qHTXaLrr6iy3tQhT3UZ"
            }
        """.trimIndent()

        val identityCredential = """
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
                "sd": false
              }
            },
            "decoyMode": "NONE",
            "decoys": 0
          },
          "authenticationMethod": "PRE_AUTHORIZED",
          "x5Chain": [
            "-----BEGIN CERTIFICATE-----\nMIIBeTCCAR8CFHrWgrGl5KdefSvRQhR+aoqdf48+MAoGCCqGSM49BAMCMBcxFTATBgNVBAMMDE1ET0MgUk9PVCBDQTAgFw0yNTA1MTQxNDA4MDlaGA8yMDc1MDUwMjE0MDgwOVowZTELMAkGA1UEBhMCQVQxDzANBgNVBAgMBlZpZW5uYTEPMA0GA1UEBwwGVmllbm5hMRAwDgYDVQQKDAd3YWx0LmlkMRAwDgYDVQQLDAd3YWx0LmlkMRAwDgYDVQQDDAd3YWx0LmlzMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEG0RINBiF+oQUD3d5DGnegQuXenI29JDaMGoMvioKRBN53d4UazakS2unu8BnsEtxutS2kqRhYBPYk9RAriU3gTAKBggqhkjOPQQDAgNIADBFAiAOMwM7hH7q9Di+mT6qCi4LvB+kH8OxMheIrZ2eRPxtDQIhALHzTxwvN8Udt0Z2Cpo8JBihqacfeXkIxVAO8XkxmXhB\n-----END CERTIFICATE-----"
          ],
          "trustedRootCAs": [
            "-----BEGIN CERTIFICATE-----\nMIIBZTCCAQugAwIBAgII2x50/ui7K2wwCgYIKoZIzj0EAwIwFzEVMBMGA1UEAwwMTURPQyBST09UIENBMCAXDTI1MDUxNDE0MDI1M1oYDzIwNzUwNTAyMTQwMjUzWjAXMRUwEwYDVQQDDAxNRE9DIFJPT1QgQ0EwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAARY/Swb4KSMi1n0p8zewsX6ssZvwdgJ+eWwgf81YmOJeRPHnuvIMth9NTpBdi6RUodKrowR5u9A+pMlPVuVn/F4oz8wPTAMBgNVHRMBAf8EAjAAMA4GA1UdDwEB/wQEAwIBBjAdBgNVHQ4EFgQUxaGwGuK+ZbdzYNqADTyJ/gqLRwkwCgYIKoZIzj0EAwIDSAAwRQIhAOEYhbDYF/1kgDgy4anwZfoULmwt4vt08U6EU2AjXI09AiACCM7m3FnO7bc+xYQRT+WBkZXe/Om4bVmlIK+av+SkCA==\n-----END CERTIFICATE-----\n"
          ]
        }
    """.trimIndent()


    }

}
