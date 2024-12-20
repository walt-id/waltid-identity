@file:OptIn(ExperimentalUuidApi::class)

import id.walt.commons.testing.E2ETest.test
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyType
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

class PresentationDefinitionPolicyTests {

    private val email = "${Uuid.random()}@mail.com"
    private val password = Uuid.random().toString()


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
            description = "Presentation Definition Policy Scenario - IETF SD-JWT VC Identity Credential, ",
            setup = {
                issueCredentialsToWallet(
                    issuanceRequests = listOf(
                        IssuanceRequests.identityCredential,
                    )
                )
            },
            evaluate = {
                evaluatePresentationVerificationResult(
                    presentationRequest = PresentationRequests.identityCredential,
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
        test(
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

        val identityCredential = """
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
                            "const": false
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
            "-----BEGIN CERTIFICATE-----\nMIIBRzCB7qADAgECAgg57ch6mnj5KjAKBggqhkjOPQQDAjAXMRUwEwYDVQQDDAxNRE9DIFJPT1QgQ0EwHhcNMjQwNTAyMTMxMzMwWhcNMjUwNTAyMTMxMzMwWjAbMRkwFwYDVQQDDBBNRE9DIFRlc3QgSXNzdWVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEG0RINBiF+oQUD3d5DGnegQuXenI29JDaMGoMvioKRBN53d4UazakS2unu8BnsEtxutS2kqRhYBPYk9RAriU3gaMgMB4wDAYDVR0TAQH/BAIwADAOBgNVHQ8BAf8EBAMCB4AwCgYIKoZIzj0EAwIDSAAwRQIhAI5wBBAA3ewqIwslhuzFn4rNFW9dkz2TY7xeImO7CraYAiAYhai1NzJ6abAiYg8HxcRdYpO4bu2Sej8E6CzFHK34Yw==\n-----END CERTIFICATE-----"
          ],
          "trustedRootCAs": [
            "-----BEGIN CERTIFICATE-----\nMIIBQzCB66ADAgECAgjbHnT+6LsrbDAKBggqhkjOPQQDAjAYMRYwFAYDVQQDDA1NRE9DIFJPT1QgQ1NQMB4XDTI0MDUwMjEzMTMzMFoXDTI0MDUwMzEzMTMzMFowFzEVMBMGA1UEAwwMTURPQyBST09UIENBMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEWP0sG+CkjItZ9KfM3sLF+rLGb8HYCfnlsIH/NWJjiXkTx57ryDLYfTU6QXYukVKHSq6MEebvQPqTJT1blZ/xeKMgMB4wDAYDVR0TAQH/BAIwADAOBgNVHQ8BAf8EBAMCAQYwCgYIKoZIzj0EAwIDRwAwRAIgWM+JtnhdqbTzFD1S3byTvle0n/6EVALbkKCbdYGLn8cCICOoSETqwk1oPnJEEPjUbdR4txiNqkHQih8HKAQoe8t5\n-----END CERTIFICATE-----\n"
          ]
        }
    """.trimIndent()


    }

}