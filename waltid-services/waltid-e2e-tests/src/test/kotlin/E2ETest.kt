import E2ETestWebService.test
import E2ETestWebService.testBlock
import id.walt.commons.config.ConfigManager
import id.walt.commons.web.plugins.httpJson
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.oid4vc.data.*
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.CredentialOfferRequest
import id.walt.verifier.oidc.PresentationSessionInfo
import id.walt.webwallet.config.RegistrationDefaultsConfig
import id.walt.webwallet.db.models.Account
import id.walt.webwallet.db.models.AccountWalletListing
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.db.models.WalletOperationHistory
import id.walt.webwallet.web.controllers.UsePresentationRequest
import id.walt.webwallet.web.model.AccountRequest
import id.walt.webwallet.web.model.EmailAccountRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.util.*
import io.ktor.util.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlinx.uuid.UUID
import java.io.File
import java.net.URLDecoder
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes

class E2ETest {
    lateinit var wallet: UUID
    lateinit var newCredentialId: String
    lateinit var verificationUrl: String
    lateinit var verificationId: String
    lateinit var did: String
    lateinit var offerUrl: String

    var client = testHttpClient()

    @Test
    fun e2eInit() = runTest(timeout = 5.minutes) {
        testBlock {

            // the e2e http request tests here

            //region -Login-
            test("/wallet-api/auth/user-info - not logged in without token") {
                client.get("/wallet-api/auth/user-info").apply {
                    assert(status == HttpStatusCode.Unauthorized) { "Was authorized without authorizing!" }
                }
            }

            test("/wallet-api/auth/login - wallet-api login") {
                client.post("/wallet-api/auth/login") {
                    setBody(
                        EmailAccountRequest(
                            email = "user@email.com", password = "password"
                        ) as AccountRequest
                    )
                }.expectSuccess().apply {
                    body<JsonObject>().let { result ->
                        assertNotNull(result["token"])
                        val token = result["token"]!!.jsonPrimitive.content.expectLooksLikeJwt()

                        client = testHttpClient(token = token)
                    }
                }
            }

            lateinit var accountId: UUID

            test("/wallet-api/auth/user-info - logged in after login") {
                client.get("/wallet-api/auth/user-info").expectSuccess().apply {
                    body<Account>().let { account ->
                        accountId = account.id
                    }
                }
            }

            test("/wallet-api/auth/session - logged in after login") {
                client.get("/wallet-api/auth/session").expectSuccess()
            }

            test("/wallet-api/wallet/accounts/wallets - get wallets") {
                client.get("/wallet-api/wallet/accounts/wallets").expectSuccess().apply {
                    val listing = body<AccountWalletListing>()
                    assert(listing.account == accountId) { "Wallet listing is for wrong account!" }

                    assert(listing.wallets.isNotEmpty()) { "No wallets available!" }
                    wallet = listing.wallets.first().id
                    println("Selected wallet: $wallet")
                }
            }
            //endregion -Login-

            //region -Keys-
            val keysApi = KeysApi(client)
            val defaultKeyConfig = ConfigManager.getConfig<RegistrationDefaultsConfig>().defaultKeyConfig
            val keyGenRequest = KeyGenerationRequest("jwk", KeyType.Ed25519)
            lateinit var generatedKeyId: String
            val rsaJwkImport = loadResource("keys/rsa.json")
            keysApi.list(wallet, defaultKeyConfig)
            keysApi.generate(wallet, keyGenRequest) { generatedKeyId = it }
            keysApi.load(wallet, generatedKeyId, keyGenRequest)
            keysApi.meta(wallet, generatedKeyId, keyGenRequest)
            keysApi.export(wallet, generatedKeyId, "JWK", true, keyGenRequest)
            keysApi.delete(wallet, generatedKeyId)
            keysApi.import(wallet, rsaJwkImport)
            //endregion -Keys-

            val didsApi = DidsApi(client)
            //region -Dids-
            val createdDids = mutableListOf<String>()
            didsApi.list(wallet, 1, DidsApi.DefaultDidOption.Any) {
                assert(it.first().default)
                did = it.first().did
            }
            didsApi.create(wallet, DidsApi.DidCreateRequest(method = "key", options = mapOf("useJwkJcsPub" to false))) {
                createdDids.add(it)
            }
            didsApi.create(wallet, DidsApi.DidCreateRequest(method = "jwk")) {
                createdDids.add(it)
            }
            didsApi.create(
                wallet,
                DidsApi.DidCreateRequest(method = "web", options = mapOf("domain" to "domain", "path" to "path"))
            ) {
                createdDids.add(it)
            }
            didsApi.create(
                wallet, DidsApi.DidCreateRequest(method = "cheqd", options = mapOf("network" to "testnet"))
            ) {
                createdDids.add(it)
            }
            //TODO: error(400) DID method not supported for auto-configuration: ebsi
//            didsApi.create(wallet, DidsApi.DidCreateRequest(method = "ebsi", options = mapOf("version" to 2, "bearerToken" to "token"))){
//                createdDids.add(it)
//            }
            //TODO: didsApi.create(wallet, DidsApi.DidCreateRequest(method = "iota")){ createdDids.add(it) }
            didsApi.default(wallet, createdDids[0])
            didsApi.list(wallet, createdDids.size + 1, DidsApi.DefaultDidOption.Some(createdDids[0]))
            for (d in createdDids) {
                didsApi.delete(wallet, d)
            }
            didsApi.list(wallet, 1, DidsApi.DefaultDidOption.None)
            didsApi.get(wallet, did)
            didsApi.default(wallet, did)
            didsApi.list(wallet, 1, DidsApi.DefaultDidOption.Some(did))
            //endregion -Dids-

            test("/wallet-api/wallet/{wallet}/credentials - list credentials") {
                client.get("/wallet-api/wallet/$wallet/credentials").expectSuccess().apply {
                    val credentials = body<List<JsonObject>>()
                    assert(credentials.isEmpty()) { "should not have any credentials yet" }
                }
            }

            // Execute Authorize Flow with ID Token request as authentication method
            e2eAuthorize()

            // Execute PreAuthorized Flow
            e2ePreAuthorized()

            // Execute Verification after PreAuthorized Flow
            e2eVerification()

            test("/openid4vc/session/{id} - check if presentation definitions match") {
                client.get("/openid4vc/session/$verificationId").expectSuccess().apply {
                    val info = body<PresentationSessionInfo>()

                    assert(info.tokenResponse?.vpToken?.jsonPrimitive?.contentOrNull?.expectLooksLikeJwt() != null) { "Received no valid token response!" }
                    assert(info.tokenResponse?.presentationSubmission != null) { "should have a presentation submission after submission" }

                    assert(info.verificationResult == true) { "overall verification should be valid" }
                    info.policyResults.let {
                        require(it != null) { "policyResults should be available after running policies" }
                        assert(it.size > 1) { "no policies have run" }
                    }
                }
            }

            test("/wallet-api/wallet/{wallet}/history - get operation history") {
                client.get("/wallet-api/wallet/$wallet/history").expectSuccess().apply {
                    val history = body<List<WalletOperationHistory>>()
                    assert(history.size >= 2) { "missing history items" }
                    assert(history.any { it.operation == "useOfferRequest" } && history.any { it.operation == "usePresentationRequest" }) { "incorrect history items" }
                }
            }


        }
    }

    private fun e2ePreAuthorized() = runTest(timeout = 5.minutes) {
        test("/openid4vc/jwt/issue - issue credential with PreAuthorized Code Flow") {
            client.post("/openid4vc/jwt/issue") {
                //language=JSON
                setBody(
                    """
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
                      "credentialConfigurationId": "OpenBadgeCredential_jwt_vc_json",
                      "credentialData": {
                        "@context": [
                          "https://www.w3.org/2018/credentials/v1",
                          "https://purl.imsglobal.org/spec/ob/v3p0/context.json"
                        ],
                        "id": "urn:uuid:THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION (see below)",
                        "type": [
                          "VerifiableCredential",
                          "OpenBadgeCredential"
                        ],
                        "name": "JFF x vc-edu PlugFest 3 Interoperability",
                        "issuer": {
                          "type": [
                            "Profile"
                          ],
                          "id": "did:key:THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION FROM CONTEXT (see below)",
                          "name": "Jobs for the Future (JFF)",
                          "url": "https://www.jff.org/",
                          "image": "https://w3c-ccg.github.io/vc-ed/plugfest-1-2022/images/JFF_LogoLockup.png"
                        },
                        "issuanceDate": "2023-07-20T07:05:44Z (THIS WILL BE REPLACED BY DYNAMIC DATA FUNCTION (see below))",
                        "expirationDate": "WILL BE MAPPED BY DYNAMIC DATA FUNCTION (see below)",
                        "credentialSubject": {
                          "id": "did:key:123 (THIS WILL BE REPLACED BY DYNAMIC DATA FUNCTION (see below))",
                          "type": [
                            "AchievementSubject"
                          ],
                          "achievement": {
                            "id": "urn:uuid:ac254bd5-8fad-4bb1-9d29-efd938536926",
                            "type": [
                              "Achievement"
                            ],
                            "name": "JFF x vc-edu PlugFest 3 Interoperability",
                            "description": "This wallet supports the use of W3C Verifiable Credentials and has demonstrated interoperability during the presentation request workflow during JFF x VC-EDU PlugFest 3.",
                            "criteria": {
                              "type": "Criteria",
                              "narrative": "Wallet solutions providers earned this badge by demonstrating interoperability during the presentation request workflow. This includes successfully receiving a presentation request, allowing the holder to select at least two types of verifiable credentials to create a verifiable presentation, returning the presentation to the requestor, and passing verification of the presentation and the included credentials."
                            },
                            "image": {
                              "id": "https://w3c-ccg.github.io/vc-ed/plugfest-3-2023/images/JFF-VC-EDU-PLUGFEST3-badge-image.png",
                              "type": "Image"
                            }
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
                      }
                    }
                    """.trimIndent()
                )
            }.expectSuccess().apply {
                offerUrl = body<String>()
                println("offer: $offerUrl")
            }
        }

        test("/wallet-api/wallet/{wallet}/exchange/resolveCredentialOffer - resolve credential offer") {
            client.post("/wallet-api/wallet/$wallet/exchange/resolveCredentialOffer") {
                setBody(offerUrl)
            }.expectSuccess()
        }


        test("/wallet-api/wallet/{wallet}/exchange/useOfferRequest - claim credential from issuer") {
            client.post("/wallet-api/wallet/$wallet/exchange/useOfferRequest") {
                setBody(offerUrl)
            }.expectSuccess().run {
                val newCredentials = body<List<WalletCredential>>()
                assert(newCredentials.size == 1) { "should have received a credential" }

                val cred = newCredentials.first()
                newCredentialId = cred.id
                newCredentialId
            }
        }

        test("/wallet-api/wallet/{wallet}/credentials - list credentials after issuance") {
            client.get("/wallet-api/wallet/$wallet/credentials").expectSuccess().apply {
                val credentials = body<List<WalletCredential>>()
                assert(credentials.size == 1) { "should have exactly 1 credential by now" }

                assert(credentials.first().id == newCredentialId) { "credential should be the one received" }
                credentials.map { it.id }
            }
        }

    }

    private fun e2eVerification() = runTest(timeout = 5.minutes){
        test("/openid4vc/verify") {
            client.post("/openid4vc/verify") {
                //language=JSON
                setBody(
                    """
                        {
                          "request_credentials": [
                            "OpenBadgeCredential"
                          ]
                        }
                    """.trimIndent()
                )
            }.expectSuccess().apply {
                verificationUrl = body<String>()
            }
            assert(verificationUrl.contains("presentation_definition_uri="))
            assert(!verificationUrl.contains("presentation_definition="))

            verificationId = Url(verificationUrl).parameters.getOrFail("state")

            verificationUrl
        }

        lateinit var resolvedPresentationOfferString: String
        lateinit var presentationDefinition: String
        test("/wallet-api/wallet/{wallet}/exchange/resolvePresentationRequest - get presentation definition") {
            client.post("/wallet-api/wallet/$wallet/exchange/resolvePresentationRequest") {
                contentType(ContentType.Text.Plain)
                setBody(verificationUrl)
            }.expectSuccess().apply {
                resolvedPresentationOfferString = body<String>()
            }
            assert(resolvedPresentationOfferString.contains("presentation_definition="))

            presentationDefinition =
                Url(resolvedPresentationOfferString).parameters.getOrFail("presentation_definition")

            presentationDefinition
        }

        test("/openid4vc/session/{id} - check if presentation definitions match") {
            client.get("/openid4vc/session/$verificationId").expectSuccess().apply {
                val info = body<PresentationSessionInfo>()

                assert(info.presentationDefinition == PresentationDefinition.fromJSONString(presentationDefinition))
            }
        }

        test("/wallet-api/wallet/{wallet}/exchange/matchCredentialsForPresentationDefinition - should match OpenBadgeCredential in wallet") {
            client.post("/wallet-api/wallet/$wallet/exchange/matchCredentialsForPresentationDefinition") {
                setBody(presentationDefinition)
            }.expectSuccess().run {
                val matched = body<List<WalletCredential>>()
                assert(matched.size == 1) { "presentation definition should match 1 credential" }
                assert(newCredentialId == matched.first().id) { "matched credential should be the received one" }
                matched.map { it.id }
            }
        }

        test("/wallet-api/wallet/{wallet}/exchange/unmatchedCredentialsForPresentationDefinition - none should be missing") {
            client.post("/wallet-api/wallet/$wallet/exchange/unmatchedCredentialsForPresentationDefinition") {
                setBody(presentationDefinition)
            }.expectSuccess().run {
                val unmatched = body<List<WalletCredential>>()
                assert(unmatched.isEmpty()) { "should not have not matched credentials (all 1 credential should match)" }
                unmatched
            }
        }

        test("/wallet-api/wallet/{wallet}/exchange/usePresentationRequest - present credentials") {
            client.post("/wallet-api/wallet/$wallet/exchange/usePresentationRequest") {
                setBody(
                    UsePresentationRequest(
                        did = did,
                        presentationRequest = resolvedPresentationOfferString,
                        selectedCredentials = listOf(newCredentialId)
                    )
                )
            }.expectSuccess()
        }

    }

    private fun e2eAuthorize() = runTest(timeout = 5.minutes) {
        lateinit var issuerState: String
        var authorizationRequest =AuthorizationRequest(
                clientId = "did:key:xzy",
                scope = setOf("openid"),
                clientMetadata = OpenIDClientMetadata(
                    requestUris = listOf("openid://redirect"),
                    jwksUri = "myuri.com/jwks",
                    customParameters = mapOf("authorization_endpoint" to "openid://".toJsonElement()),
                ),
                authorizationDetails = emptyList(),
                requestUri = "openid://redirect",
                responseType = setOf(ResponseType.Code),
                state = "UPD4Qjo2gzBNv641YQf19BamZets1xQpkY8jYTxvqq8",
                issuerState = "issuerState",
                codeChallenge = "UPD4Qjo2gzBNv641YQf19BamZets1xQpkY8jYTxvqq8",
                codeChallengeMethod = "S256"
        )

        test("/openid4vc/jwt/issue - issue credential with Authorized Code Flow and Id Token request") {
            client.post("/openid4vc/jwt/issue") {
                //language=JSON
                setBody(
                    """
                    {
                          "authenticationMethod": "ID_TOKEN",
                          "useJar": true,
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
                          "credentialConfigurationId": "OpenBadgeCredential_jwt_vc_json",
                          "credentialData": {
                            "@context": [
                              "https://www.w3.org/2018/credentials/v1",
                              "https://purl.imsglobal.org/spec/ob/v3p0/context.json"
                            ],
                            "id": "urn:uuid:THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION (see below)",
                            "type": [
                              "VerifiableCredential",
                              "OpenBadgeCredential"
                            ],
                            "name": "JFF x vc-edu PlugFest 3 Interoperability",
                            "issuer": {
                              "type": [
                                "Profile"
                              ],
                              "id": "did:key:THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION FROM CONTEXT (see below)",
                              "name": "Jobs for the Future (JFF)",
                              "url": "https://www.jff.org/",
                              "image": "https://w3c-ccg.github.io/vc-ed/plugfest-1-2022/images/JFF_LogoLockup.png"
                            },
                            "issuanceDate": "2023-07-20T07:05:44Z (THIS WILL BE REPLACED BY DYNAMIC DATA FUNCTION (see below))",
                            "expirationDate": "WILL BE MAPPED BY DYNAMIC DATA FUNCTION (see below)",
                            "credentialSubject": {
                              "id": "did:key:123 (THIS WILL BE REPLACED BY DYNAMIC DATA FUNCTION (see below))",
                              "type": [
                                "AchievementSubject"
                              ],
                              "achievement": {
                                "id": "urn:uuid:ac254bd5-8fad-4bb1-9d29-efd938536926",
                                "type": [
                                  "Achievement"
                                ],
                                "name": "JFF x vc-edu PlugFest 3 Interoperability",
                                "description": "This wallet supports the use of W3C Verifiable Credentials and has demonstrated interoperability during the presentation request workflow during JFF x VC-EDU PlugFest 3.",
                                "criteria": {
                                  "type": "Criteria",
                                  "narrative": "Wallet solutions providers earned this badge by demonstrating interoperability during the presentation request workflow. This includes successfully receiving a presentation request, allowing the holder to select at least two types of verifiable credentials to create a verifiable presentation, returning the presentation to the requestor, and passing verification of the presentation and the included credentials."
                                },
                                "image": {
                                  "id": "https://w3c-ccg.github.io/vc-ed/plugfest-3-2023/images/JFF-VC-EDU-PLUGFEST3-badge-image.png",
                                  "type": "Image"
                                }
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
                          }
                    }
                    """.trimIndent()
                )
            }.expectSuccess().apply {
                offerUrl = body<String>()
                val offerUrlParams = Url(offerUrl).parameters.toMap()
                val offerObj = CredentialOfferRequest.fromHttpParameters(offerUrlParams)
                val credOffer = client.get(offerObj.credentialOfferUri!!).body<JsonObject>()
                issuerState = credOffer["grants"]!!.jsonObject["authorization_code"]!!.jsonObject["issuer_state"]!!.jsonPrimitive.content
                authorizationRequest = authorizationRequest.copy(
                    issuerState = issuerState,
                    authorizationDetails = listOf(
                        AuthorizationDetails(
                            type = "openid_credential",
                            locations = listOf(credOffer["credential_issuer"]!!.jsonPrimitive.content),
                            format = CredentialFormat.jwt_vc,
                            types = listOf("VerifiableCredential","OpenBadgeCredential")
                        )
                    )
                )
            }
        }

        test("/authorize - make authorize request with Authorized Code Flow and Id Token request") {
            client.get("/authorize?${authorizationRequest.toHttpQueryString()}") {
            }.expectRedirect().apply {
                val idTokenRequest = AuthorizationRequest.fromHttpQueryString(headers["location"]!!)
                assert(idTokenRequest.responseType == setOf(ResponseType.IdToken)) { "response type should be id_token" }
                assert(idTokenRequest.responseMode == ResponseMode.direct_post) { "response mode should be direct post" }
            }
        }

        test("/openid4vc/jwt/issue - issue credential with Authorized Code Flow and Vp Token request") {
            client.post("/openid4vc/jwt/issue") {
                //language=JSON
                setBody(
                    """
                    {
                          "authenticationMethod": "VP_TOKEN",
                          "vpRequestValue": "NaturalPersonVerifiableID",
                          "vpProfile": "EBSIV3",
                          "useJar": true,
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
                          "credentialConfigurationId": "OpenBadgeCredential_jwt_vc_json",
                          "credentialData": {
                            "@context": [
                              "https://www.w3.org/2018/credentials/v1",
                              "https://purl.imsglobal.org/spec/ob/v3p0/context.json"
                            ],
                            "id": "urn:uuid:THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION (see below)",
                            "type": [
                              "VerifiableCredential",
                              "OpenBadgeCredential"
                            ],
                            "name": "JFF x vc-edu PlugFest 3 Interoperability",
                            "issuer": {
                              "type": [
                                "Profile"
                              ],
                              "id": "did:key:THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION FROM CONTEXT (see below)",
                              "name": "Jobs for the Future (JFF)",
                              "url": "https://www.jff.org/",
                              "image": "https://w3c-ccg.github.io/vc-ed/plugfest-1-2022/images/JFF_LogoLockup.png"
                            },
                            "issuanceDate": "2023-07-20T07:05:44Z (THIS WILL BE REPLACED BY DYNAMIC DATA FUNCTION (see below))",
                            "expirationDate": "WILL BE MAPPED BY DYNAMIC DATA FUNCTION (see below)",
                            "credentialSubject": {
                              "id": "did:key:123 (THIS WILL BE REPLACED BY DYNAMIC DATA FUNCTION (see below))",
                              "type": [
                                "AchievementSubject"
                              ],
                              "achievement": {
                                "id": "urn:uuid:ac254bd5-8fad-4bb1-9d29-efd938536926",
                                "type": [
                                  "Achievement"
                                ],
                                "name": "JFF x vc-edu PlugFest 3 Interoperability",
                                "description": "This wallet supports the use of W3C Verifiable Credentials and has demonstrated interoperability during the presentation request workflow during JFF x VC-EDU PlugFest 3.",
                                "criteria": {
                                  "type": "Criteria",
                                  "narrative": "Wallet solutions providers earned this badge by demonstrating interoperability during the presentation request workflow. This includes successfully receiving a presentation request, allowing the holder to select at least two types of verifiable credentials to create a verifiable presentation, returning the presentation to the requestor, and passing verification of the presentation and the included credentials."
                                },
                                "image": {
                                  "id": "https://w3c-ccg.github.io/vc-ed/plugfest-3-2023/images/JFF-VC-EDU-PLUGFEST3-badge-image.png",
                                  "type": "Image"
                                }
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
                          }
                    }
                    """.trimIndent()
                )
            }.expectSuccess().apply {
                offerUrl = body<String>()
                val offerUrlParams = Url(offerUrl).parameters.toMap()
                val offerObj = CredentialOfferRequest.fromHttpParameters(offerUrlParams)
                val credOffer = client.get(offerObj.credentialOfferUri!!).body<JsonObject>()
                issuerState = credOffer["grants"]!!.jsonObject["authorization_code"]!!.jsonObject["issuer_state"]!!.jsonPrimitive.content
                authorizationRequest = authorizationRequest.copy(
                    issuerState = issuerState,
                    authorizationDetails = listOf(
                        AuthorizationDetails(
                            type = "openid_credential",
                            locations = listOf(credOffer["credential_issuer"]!!.jsonPrimitive.content),
                            format = CredentialFormat.jwt_vc,
                            types = listOf("VerifiableCredential","OpenBadgeCredential")
                        )
                    )
                )
            }
        }

        test("/authorize - make authorize request with Authorized Code Flow and Vp Token request") {
            client.get("/authorize?${authorizationRequest.toHttpQueryString()}") {
            }.expectRedirect().apply {
                val vpTokenRequest = AuthorizationRequest.fromHttpQueryString(headers["location"]!!)
                assert(vpTokenRequest.responseType == setOf(ResponseType.VpToken)) { "response type should be vp_token" }
                assert(vpTokenRequest.responseMode == ResponseMode.direct_post) { "response mode should be direct post" }
                assert(vpTokenRequest.presentationDefinition != null) { "presentation definition should exists" }
            }
        }

        test("/openid4vc/jwt/issue - issue credential with Authorized Code Flow and Username/Password") {
            client.post("/openid4vc/jwt/issue") {
                //language=JSON
                setBody(
                    """
                    {
                          "authenticationMethod": "PWD",
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
                          "credentialConfigurationId": "OpenBadgeCredential_jwt_vc_json",
                          "credentialData": {
                            "@context": [
                              "https://www.w3.org/2018/credentials/v1",
                              "https://purl.imsglobal.org/spec/ob/v3p0/context.json"
                            ],
                            "id": "urn:uuid:THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION (see below)",
                            "type": [
                              "VerifiableCredential",
                              "OpenBadgeCredential"
                            ],
                            "name": "JFF x vc-edu PlugFest 3 Interoperability",
                            "issuer": {
                              "type": [
                                "Profile"
                              ],
                              "id": "did:key:THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION FROM CONTEXT (see below)",
                              "name": "Jobs for the Future (JFF)",
                              "url": "https://www.jff.org/",
                              "image": "https://w3c-ccg.github.io/vc-ed/plugfest-1-2022/images/JFF_LogoLockup.png"
                            },
                            "issuanceDate": "2023-07-20T07:05:44Z (THIS WILL BE REPLACED BY DYNAMIC DATA FUNCTION (see below))",
                            "expirationDate": "WILL BE MAPPED BY DYNAMIC DATA FUNCTION (see below)",
                            "credentialSubject": {
                              "id": "did:key:123 (THIS WILL BE REPLACED BY DYNAMIC DATA FUNCTION (see below))",
                              "type": [
                                "AchievementSubject"
                              ],
                              "achievement": {
                                "id": "urn:uuid:ac254bd5-8fad-4bb1-9d29-efd938536926",
                                "type": [
                                  "Achievement"
                                ],
                                "name": "JFF x vc-edu PlugFest 3 Interoperability",
                                "description": "This wallet supports the use of W3C Verifiable Credentials and has demonstrated interoperability during the presentation request workflow during JFF x VC-EDU PlugFest 3.",
                                "criteria": {
                                  "type": "Criteria",
                                  "narrative": "Wallet solutions providers earned this badge by demonstrating interoperability during the presentation request workflow. This includes successfully receiving a presentation request, allowing the holder to select at least two types of verifiable credentials to create a verifiable presentation, returning the presentation to the requestor, and passing verification of the presentation and the included credentials."
                                },
                                "image": {
                                  "id": "https://w3c-ccg.github.io/vc-ed/plugfest-3-2023/images/JFF-VC-EDU-PLUGFEST3-badge-image.png",
                                  "type": "Image"
                                }
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
                          }
                    }
                    """.trimIndent()
                )
            }.expectSuccess().apply {
                offerUrl = body<String>()
                val offerUrlParams = Url(offerUrl).parameters.toMap()
                val offerObj = CredentialOfferRequest.fromHttpParameters(offerUrlParams)
                val credOffer = client.get(offerObj.credentialOfferUri!!).body<JsonObject>()
                issuerState = credOffer["grants"]!!.jsonObject["authorization_code"]!!.jsonObject["issuer_state"]!!.jsonPrimitive.content
                authorizationRequest = authorizationRequest.copy(
                    issuerState = issuerState,
                    authorizationDetails = listOf(
                        AuthorizationDetails(
                            type = "openid_credential",
                            locations = listOf(credOffer["credential_issuer"]!!.jsonPrimitive.content),
                            format = CredentialFormat.jwt_vc,
                            types = listOf("VerifiableCredential","OpenBadgeCredential")
                        )
                    )
                )            }
        }

        test("/authorize - make authorize request with Authorized Code Flow and Username/Password") {
            client.get("/authorize?${authorizationRequest.toHttpQueryString()}") {
            }.expectRedirect().apply {
                assertEquals(true, headers["location"]!!.toString().contains("external_login"))
            }
        }
    }


        fun testHttpClient(token: String? = null) = HttpClient(CIO) {
        followRedirects = false
        install(ContentNegotiation) {
            json(httpJson)
        }
        install(DefaultRequest) {
            contentType(ContentType.Application.Json)
            host = "127.0.0.1"
            port = 22222

            if (token != null) bearerAuth(token)
        }
        install(Logging) {
            level = LogLevel.ALL
        }
    }
}

fun String.expectLooksLikeJwt(): String =
    also { assert(startsWith("ey") && count { it == '.' } == 2) { "Does not look like JWT" } }

fun HttpResponse.expectSuccess(): HttpResponse = also {
    assert(status.isSuccess()) { "HTTP status is non-successful" }
}

fun HttpResponse.expectRedirect(): HttpResponse = also {
    assert(status == HttpStatusCode.Found) { "HTTP status is non-successful" }
}

fun JsonElement.tryGetData(key: String): JsonElement? = key.split('.').let {
    var element: JsonElement? = this
    for (i in it) {
        element = when (element) {
            is JsonObject -> element[i]
            is JsonArray -> element.firstOrNull {
                it.jsonObject.containsKey(i)
            }?.let {
                it.jsonObject[i]
            }

            else -> element?.jsonPrimitive
        }
    }
    element
}

fun loadResource(relativePath: String): String =
    URLDecoder.decode(object {}.javaClass.getResource(relativePath)!!.path, "UTF-8").let { File(it).readText() }
