import E2ETestWebService.inlineTest
import E2ETestWebService.inlineTestWithResult
import E2ETestWebService.loadResource
import E2ETestWebService.test
import E2ETestWebService.testBlock
import E2ETestWebService.testGroup
import E2ETestWebService.testWithResult
import id.walt.commons.config.ConfigManager
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyType
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.oid4vc.data.OpenId4VPProfile
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.webwallet.config.RegistrationDefaultsConfig
import id.walt.webwallet.db.models.AccountWalletListing
import id.walt.webwallet.web.controllers.exchange.UsePresentationRequest
import id.walt.webwallet.web.model.AccountRequest
import id.walt.webwallet.web.model.EmailAccountRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.util.*
import kotlinx.serialization.json.*
import kotlinx.uuid.UUID
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.minutes

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class E2ETest {

    companion object {
        val defaultTestTimeout = 5.minutes
        val defaultEmailAccount = EmailAccountRequest(
            email = "user@email.com", password = "password"
        )
        val issuerKey = loadResource("issuance/key.json")
        val issuerDid = loadResource("issuance/did.txt")
        val openBadgeCredentialData = loadResource("issuance/openbadgecredential.json")
        val credentialMapping = loadResource("issuance/mapping.json")
        val credentialDisclosure = loadResource("issuance/disclosure.json")
        val sdjwtCredential = buildJsonObject {
            put("issuerKey", Json.decodeFromString<JsonElement>(issuerKey))
            put("issuerDid", issuerDid)
            put("credentialConfigurationId", "OpenBadgeCredential_jwt_vc_json")
            put("credentialData", Json.decodeFromString<JsonElement>(openBadgeCredentialData))
            put("mapping", Json.decodeFromString<JsonElement>(credentialMapping))
            put("selectiveDisclosure", Json.decodeFromString<JsonElement>(credentialDisclosure))
            put("authenticationMethod", "PRE_AUTHORIZED")
        }
        val jwtCredential = JsonObject(sdjwtCredential.minus("selectiveDisclosure"))
        val simplePresentationRequestPayload =
            loadResource("presentation/openbadgecredential-presentation-request.json")
        val nameFieldSchemaPresentationRequestPayload =
            loadResource("presentation/openbadgecredential-name-field-presentation-request.json")

        val walletHttpPort: Int = 22222
        // val walletHttpPort: Int = 7001
        val issuerHttpPort: Int = 22222
        // val issuerHttp: Int = 7002
        val verifierHttpPort: Int = 22222
        // val verifierHttp: Int = 7003
    }

    private lateinit var walletHttp: HttpClient
    private lateinit var issuerHttp: HttpClient
    private lateinit var verifierHttp: HttpClient


    @BeforeAll
    fun setup() {
        walletHttp = testHttpClient(port = walletHttpPort)
        issuerHttp = testHttpClient(port = issuerHttpPort)
        verifierHttp = testHttpClient(port = verifierHttpPort)
    }

    @Test
    fun e2e() = testBlock(defaultTestTimeout) {
        lateinit var wallet: UUID

        // the e2e http request tests here
        testGroup("Authentication") {
            var authApi = AuthApi(walletHttp)
            with(authApi) {
                test("Should be unauthorized when not logged in") {
                    userInfo(HttpStatusCode.Unauthorized) ?: "Unauthorized -> no account"
                }
                test("Login") {
                    val token = login(defaultEmailAccount)
                    walletHttp = testHttpClient(token, port = walletHttpPort)
                    authApi = AuthApi(walletHttp)
                    token
                }
            }
            with(authApi) {
                test("Endpoints should be usable when logged in") {
                    val account = userInfo(HttpStatusCode.OK)!!
                    userSession()
                    val wallets = userWallets(account.id)
                    wallet = wallets.first().id
                    println("Selected wallet: $wallet")
                }
            }
            ///sub-region -x5c-based authentication method test case scenarios-
            AuthApi.X5c(walletHttp).executeTestCases()
            ///end sub-region -x5c-based authentication method test case scenarios-
        }

        testGroup("Keys") {
            val keysApi = KeysApi(walletHttp, wallet)
            // requires registration-defaults to not be disabled in _features.conf
            val defaultKeyConfig = ConfigManager.getConfig<RegistrationDefaultsConfig>().defaultKeyConfig
            with(keysApi) {
                "Test key listing" inlineTest { list(defaultKeyConfig) }
                val keyGenRequest = KeyGenerationRequest("jwk", KeyType.Ed25519)
                val generatedKeyId = "Test key generation" inlineTestWithResult { generate(keyGenRequest) }
                "Test key loading" inlineTest { load(generatedKeyId, keyGenRequest) }
                "Test key metadata" inlineTest { meta(generatedKeyId, keyGenRequest) }
                "Test key exporting" inlineTest { export(generatedKeyId, "JWK", true, keyGenRequest) }
                "Test key deletion" inlineTest { delete(generatedKeyId) }
                val rsaJwkImport = loadResource("keys/rsa.json")
                "Test key import (RSA)" inlineTest { import(rsaJwkImport) }
            }
        }

        lateinit var did: String
        testGroup("DIDs") {
            val didsApi = DidsApi(walletHttp, wallet)
            with(didsApi) {
                test("Has default DID") {
                    val firstDid = list(DidsApi.DefaultDidOption.Any, 1).first()
                    assert(firstDid.default)
                    did = firstDid.did
                    firstDid
                }
                //todo: test for optional registration defaults
                val createdDids = listOf(
                    DidsApi.DidCreateRequest(method = "key", options = mapOf("useJwkJcsPub" to false)),
                    DidsApi.DidCreateRequest(method = "jwk"),
                    DidsApi.DidCreateRequest(method = "web", options = mapOf("domain" to "domain", "path" to "path")),
                    //todo: DidCreateRequest(method = "iota")
                    //todo: DidsApi.DidCreateRequest(method = "ebsi", options = mapOf("version" to 2, "bearerToken" to "token"))
                    //todo: DidsApi.DidCreateRequest(method = "cheqd", options = mapOf("network" to "testnet")) // flaky test - sometimes works fine, sometimes responds with 400
                ).map {
                    testWithResult("Create did:${it.method}") { create(it) }
                }
                test("Set default DID") {
                    setDefault(createdDids[0])
                    list(DidsApi.DefaultDidOption.Some(createdDids[0]), createdDids.size + 1)
                }
                test("Delete all created DIDs") {
                    createdDids.forEach { did -> delete(did) }
                    list(DidsApi.DefaultDidOption.None, 1)
                }
                test("Set default DID (again)") {
                    get(did)
                    setDefault(did)
                    list(DidsApi.DefaultDidOption.Some(did), 1)
                }
            }
        }

        lateinit var offerUrl: String
        val issuerApi = IssuerApi(issuerHttp)
        testGroup("Issuer / offer URL") {
            val issuanceRequest = Json.decodeFromJsonElement<IssuanceRequest>(jwtCredential)
            println("issuance-request: $issuanceRequest")
            with(issuerApi) {
                "Issue JWT" inlineTest { offerUrl = jwt(issuanceRequest); offerUrl }
            }
        }

        val exchangeApi = ExchangeApi(walletHttp, wallet)
        lateinit var newCredentialId: String
        testGroup("Claim credential") {
            "Resolve credential offer" inlineTest { exchangeApi.resolveCredentialOffer(offerUrl) }
            "Use offer request" inlineTest {
                newCredentialId = exchangeApi.useOfferRequest(offerUrl, 1).first().id; newCredentialId
            }

            newCredentialId
        }

        val credentialsApi = CredentialsApi(walletHttp, wallet)
        testGroup("Credentials") {
            with(credentialsApi) {
                "List credentials" inlineTest { list(expectedSize = 1, expectedCredential = arrayOf(newCredentialId)) }
                "View new credential" inlineTest { get(newCredentialId) }
                "Accept the new credential" inlineTest { accept(newCredentialId) }
                "Soft-delete the new credential" inlineTest { delete(newCredentialId) }
                "Restore the soft-deleted credential" inlineTest { restore(newCredentialId) }
                "View credential status" inlineTest { status(newCredentialId) }
                // reject(newCredentialId)
                // delete(newCredentialId, true)
            }
        }

        lateinit var verificationUrl: String
        lateinit var verificationId: String
        val sessionApi = Verifier.SessionApi(verifierHttp)
        val verificationApi = Verifier.VerificationApi(verifierHttp)
        testGroup("Verifier / request url") {
            "Start verification" inlineTest {
                verificationUrl = verificationApi.verify(simplePresentationRequestPayload); verificationUrl
            }
            verificationId = Url(verificationUrl).parameters.getOrFail("state")
            verificationUrl
        }

        testGroup("Exchange / presentation") {
            lateinit var resolvedPresentationOffer: String
            lateinit var presentationDefinition: String
            with(exchangeApi) {
                "Resolve " inlineTest {
                    resolvedPresentationOffer = resolvePresentationRequest(verificationUrl); resolvedPresentationOffer
                }
                presentationDefinition = Url(resolvedPresentationOffer).parameters.getOrFail("presentation_definition")
                var presentationSession = sessionApi.get(verificationId)
                assert(
                    presentationSession.presentationDefinition == PresentationDefinition.fromJSONString(
                        presentationDefinition
                    )
                )
                matchCredentialsForPresentationDefinition(presentationDefinition, listOf(newCredentialId))
                unmatchedCredentialsForPresentationDefinition(presentationDefinition)
                test("Use presentation request") {
                    usePresentationRequest(
                        UsePresentationRequest(
                            did, resolvedPresentationOffer, listOf(newCredentialId)
                        )
                    )
                }
                presentationSession = sessionApi.get(verificationId)
                assert(presentationSession.tokenResponse?.vpToken?.jsonPrimitive?.contentOrNull?.expectLooksLikeJwt() != null) { "Received no valid token response!" }
                assert(presentationSession.tokenResponse?.presentationSubmission != null) { "should have a presentation submission after submission" }
                assert(presentationSession.verificationResult == true) { "overall verification should be valid" }
                presentationSession.policyResults.let {
                    require(it != null) { "policyResults should be available after running policies" }
                    assert(it.size > 1) { "no policies have run" }
                }
            }
        }

        val categoryApi = CategoryApi(walletHttp, wallet)
        testGroup("Categories") {
            val categoryName = "name#1"
            val categoryNewName = "name#2"
            with(categoryApi) {
                "Check no categories by default" inlineTest { list(0) }

                test("Add category") {
                    add(categoryName)
                    assertNotNull(list(1).single { it["name"].asString() == categoryName })
                }

                test("Rename category") {
                    rename(categoryName, categoryNewName)
                    assertNotNull(list(1).single { it["name"].asString() == categoryNewName })
                }

                test("Delete category") {
                    delete(categoryNewName)
                    list(0)
                }

                test("Add other categories") {
                    add(categoryName)
                    add(categoryNewName)
                }
                with(credentialsApi) {
                    test("Attach categories to credentials") {
                        attachCategory(newCredentialId, categoryName, categoryNewName)
                        detachCategory(newCredentialId, categoryName, categoryNewName)
                    }
                }
            }
        }

        testGroup("History") {
            test("Test history") {
                HistoryApi(walletHttp).list(wallet).also { history ->
                    assert(history.size >= 2) { "missing history items" }
                    assert(history.any { it.operation == "useOfferRequest" } && history.any { it.operation == "usePresentationRequest" }) { "incorrect history items" }
                }.last()
            }
        }

        test("Clear up credential") {
            credentialsApi.delete(newCredentialId, true)
        }

        val lspPotentialIssuance = LspPotentialIssuance(testHttpClient(doFollowRedirects = false))
        lspPotentialIssuance.testTrack1()
        lspPotentialIssuance.testTrack2()
        val lspPotentialVerification = LspPotentialVerification(testHttpClient(doFollowRedirects = false))
        lspPotentialVerification.testPotentialInteropTrack3()
        lspPotentialVerification.testPotentialInteropTrack4()
        val lspPotentialWallet = setupTestWallet()
        lspPotentialWallet.testMDocIssuance()
        lspPotentialWallet.testMdocPresentation()
        lspPotentialWallet.testSDJwtVCIssuance()
        lspPotentialWallet.testSDJwtPresentation(OpenId4VPProfile.HAIP)
        lspPotentialWallet.testSDJwtVCIssuanceByIssuerDid()
        lspPotentialWallet.testSDJwtPresentation(OpenId4VPProfile.DEFAULT)


        // Test sdjwt-w3c issuance and presentation flows with credentialSubject.name disclosable property
        testGroup("sd-jwt w3c issuance and presentation flows") {
            with(E2ESdJwtTest(issuerApi, exchangeApi, sessionApi, verificationApi)) {
                e2e(did)
            }
        }

        // Test Authorization Code flow with available authentication methods in Issuer API
        testGroup("authorization code flow with available authentication methods in issuer-api") {
            with(AuthorizationCodeFlow(testHttpClient(doFollowRedirects = false))) {
                testIssuerAPI()
            }
        }

        // test External Signature API Endpoints
        testGroup("external signature api endpoints") {
            with(ExchangeExternalSignatures()) {
                executeTestCases()
            }
        }
    }

    //@Test // enable to execute test selectively
    fun lspIssuanceTests() = testBlock(timeout = defaultTestTimeout) {
        val client = testHttpClient(doFollowRedirects = false)
        val lspPotentialIssuance = LspPotentialIssuance(client)
        lspPotentialIssuance.testTrack1()
        lspPotentialIssuance.testTrack2()
    }

    // @Test
    fun lspVerifierTests() = testBlock(timeout = defaultTestTimeout) {
        val client = testHttpClient(doFollowRedirects = false)
        val lspPotentialVerification = LspPotentialVerification(client)
        lspPotentialVerification.testPotentialInteropTrack3()
        lspPotentialVerification.testPotentialInteropTrack4()
    }

    private suspend fun setupTestWallet(): LspPotentialWallet {
        var client = testHttpClient()
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
        val walletId = client.get("/wallet-api/wallet/accounts/wallets").expectSuccess()
            .body<AccountWalletListing>().wallets.first().id.toString()
        return LspPotentialWallet(client, walletId)
    }

    //@Test // enable to execute test selectively
    fun lspWalletTests() = testBlock(timeout = defaultTestTimeout) {
        val lspPotentialWallet = setupTestWallet()
        lspPotentialWallet.testMDocIssuance()
        lspPotentialWallet.testMdocPresentation()

        lspPotentialWallet.testSDJwtVCIssuance()
        lspPotentialWallet.testSDJwtPresentation(OpenId4VPProfile.HAIP)
    }

    //@Test // enable to execute test selectively
    fun testSdJwtVCIssuanceWithIssuerDid() = testBlock(timeout = defaultTestTimeout) {
        val lspPotentialWallet = setupTestWallet()
        lspPotentialWallet.testSDJwtVCIssuanceByIssuerDid()
        lspPotentialWallet.testSDJwtPresentation(OpenId4VPProfile.DEFAULT)
    }
}
