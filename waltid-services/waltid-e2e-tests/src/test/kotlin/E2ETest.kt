import E2EResources.defaultEmailAccount
import E2EResources.defaultTestTimeout
import E2EResources.jwtCredential
import E2EResources.nameFieldSchemaPresentationRequestPayload
import E2EResources.sdjwtCredential
import E2EResources.simplePresentationRequestPayload
import E2ETestWebService.inlineTest
import E2ETestWebService.inlineTestWithResult
import E2ETestWebService.loadResource
import E2ETestWebService.test
import E2ETestWebService.testBlock
import E2ETestWebService.testGroup
import E2ETestWebService.testWithResult
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyType
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.oid4vc.data.OpenId4VPProfile
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.webwallet.web.controllers.UsePresentationRequest
import id.walt.webwallet.config.RegistrationDefaultsConfig
import id.walt.webwallet.db.models.AccountWalletListing
import id.walt.webwallet.web.controllers.exchange.UsePresentationRequest
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
import io.ktor.server.util.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.*
import kotlinx.uuid.UUID
import kotlin.test.Test
import kotlin.test.assertNotNull

class E2ETest {

    companion object {
        val defaultTestTimeout = 5.minutes
        val defaultEmailAccount = EmailAccountRequest(
            email = "user@email.com",
            password = "password"
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
        }
        val jwtCredential = JsonObject(sdjwtCredential.minus("selectiveDisclosure"))
        val simplePresentationRequestPayload =
            loadResource("presentation/openbadgecredential-presentation-request.json")
        val nameFieldSchemaPresentationRequestPayload =
            loadResource("presentation/openbadgecredential-name-field-presentation-request.json")

        fun testHttpClient(token: String? = null, doFollowRedirects: Boolean = true) = HttpClient(CIO) {
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
            followRedirects = doFollowRedirects
        }
    }


    @Test
    fun e2e() = testBlock(defaultTestTimeout) {
        var client = testHttpClient()
        lateinit var wallet: UUID

        // E2E tests here:

        testGroup("Authentication") {
            var authApi = AuthApi(client)
            with(authApi) {
                test("Should be unauthorized when not logged in") {
                    userInfo(HttpStatusCode.Unauthorized) ?: "Unauthorized -> no account"
                }
                test("Login") {
                    val token = login(defaultEmailAccount)
                    client = testHttpClient(token)
                    authApi = AuthApi(client)
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
            AuthApi.X5c(client).executeTestCases()
            ///end sub-region -x5c-based authentication method test case scenarios-
        }

        testGroup("Keys") {
            val keysApi = KeysApi(client, wallet)
            with(keysApi) {
                "Test key listing" inlineTest { keysApi.list() }
                // requires registration-defaults to not be disabled in _features.confval defaultKeyConfig = ConfigManager.getConfig<RegistrationDefaultsConfig>().defaultKeyConfig    val keyGenRequest = KeyGenerationRequest("jwk", KeyType.Ed25519)
                val generatedKeyId = "Test key generation" inlineTestWithResult { keysApi.generate(keyGenRequest) }
                "Test key loading" inlineTest { keysApi.load(generatedKeyId, keyGenRequest) }
                "Test key metadata" inlineTest { keysApi.meta(generatedKeyId, keyGenRequest) }
                "Test key exporting" inlineTest { keysApi.export(generatedKeyId, "JWK", true, keyGenRequest) }
                "Test key deletion" inlineTest { keysApi.delete(generatedKeyId) }
                val rsaJwkImport = loadResource("keys/rsa.json")
                "Test key import (RSA)" inlineTest { keysApi.import(rsaJwkImport) }
            }
        }

        lateinit var did: String
        testGroup("DIDs") {
            val didsApi = DidsApi(client, wallet)

            with(didsApi) {
                test("Has default DID") {
                    val firstDid = list(DidsApi.DefaultDidOption.Any, 1).first()
                    assert(firstDid.default)
                    did = firstDid.did
                    firstDid
                }

                val createdDids = listOf(
                    DidsApi.DidCreateRequest(method = "key", options = mapOf("useJwkJcsPub" to false)),
                    DidsApi.DidCreateRequest(method = "jwk"),
                    DidsApi.DidCreateRequest(method = "web", options = mapOf("domain" to "domain", "path" to "path"))
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
        testGroup("Issuer / offer URL") {
            val issuerApi = IssuerApi(client)
            val issuanceRequest = Json.decodeFromJsonElement<IssuanceRequest>(jwtCredential)
            println("issuance-request: $issuanceRequest")

            "Issue JWT" inlineTest { offerUrl = issuerApi.jwt(issuanceRequest); offerUrl }
        }

        val exchangeApi = ExchangeApi(client, wallet)
        lateinit var newCredentialId: String
        testGroup("Claim credential") {
            "Resolve credential offer" inlineTest { exchangeApi.resolveCredentialOffer(offerUrl) }
            "Use offer request" inlineTest { newCredentialId = exchangeApi.useOfferRequest(offerUrl, 1).first().id; newCredentialId }

            newCredentialId
        }

        val credentialsApi = CredentialsApi(client, wallet)
        testGroup("Credentials") {
            with(credentialsApi) {
                "List credentials" inlineTest { list(expectedSize = 1, expectedCredential = arrayOf(newCredentialId)) }
                "View new credential" inlineTest { get(newCredentialId) }
                "Accept the new credential" inlineTest { accept(newCredentialId) }
                "(Temp-)Delete the new credential again " inlineTest { delete(newCredentialId) }
                "Restore the deleted credential" inlineTest { restore(newCredentialId) }
                "View credential status" inlineTest { status(newCredentialId) }
                // reject(newCredentialId)
                // delete(newCredentialId, true)
            }
        }

        lateinit var verificationUrl: String
        lateinit var verificationId: String
        val sessionApi = Verifier.SessionApi(client)
        val verificationApi = Verifier.VerificationApi(client)
        testGroup("Verifier / request url") {
            "Start verification" inlineTest { verificationUrl = verificationApi.verify(simplePresentationRequestPayload); verificationUrl }
            verificationId = Url(verificationUrl).parameters.getOrFail("state")

            verificationUrl
        }

        testGroup("Exchange / presentation") {
            lateinit var resolvedPresentationOffer: String
            lateinit var presentationDefinition: String
            with(exchangeApi) {
                "Resolve " inlineTest { resolvedPresentationOffer = resolvePresentationRequest(verificationUrl); resolvedPresentationOffer }
                presentationDefinition = Url(resolvedPresentationOffer).parameters.getOrFail("presentation_definition")

                var presentationSession = sessionApi.get(verificationId)
                assert(presentationSession.presentationDefinition == PresentationDefinition.fromJSONString(presentationDefinition))

                matchCredentialsForPresentationDefinition(presentationDefinition, listOf(newCredentialId))
                unmatchedCredentialsForPresentationDefinition(presentationDefinition)

                test("Use presentation request") {
                    usePresentationRequest(UsePresentationRequest(did, resolvedPresentationOffer, listOf(newCredentialId)))
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

        val categoryApi = CategoryApi(client, wallet)
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
            }

            with(credentialsApi) {
                test("Attach categories to credentials") {
                    attachCategory(newCredentialId, categoryName, categoryNewName)
                    detachCategory(newCredentialId, categoryName, categoryNewName)
                }
            }
        }

        testGroup("History") {
            test("Test history") {
                HistoryApi(client).list(wallet).also { history ->
                    assert(history.size >= 2) { "missing history items" }
                    assert(history.any { it.operation == "useOfferRequest" } && history.any { it.operation == "usePresentationRequest" }) { "incorrect history items" }
                }.last()
            }
        }

        test("Clear up credential") {
            credentialsApi.delete(newCredentialId, true)
        }

        testGroup("SD-JWT") {
            // todo: make this cleaner:
            test("SD-JWT") {

                //region -Issuer / offer url-
                val issuerApi = IssuerApi(client)
                val issuanceRequest = Json.decodeFromJsonElement<IssuanceRequest>(jwtCredential)
                println("issuance-request:")
                println(issuanceRequest)
                val offerUrl = issuerApi.jwt(issuanceRequest)
                //endregion -Issuer / offer url-

                //region -Exchange / claim-
                val exchangeApi = ExchangeApi(client, wallet)
                exchangeApi.resolveCredentialOffer(offerUrl)
                val newCredential = exchangeApi.useOfferRequest(offerUrl, 1).first()
                //endregion -Exchange / claim-

                //region -Credentials-
            val credentialsApi = CredentialsApi(client)
            credentialsApi.list(wallet, expectedSize = 1, expectedCredential = arrayOf(newCredentialId))
            credentialsApi.get(wallet, newCredentialId)
            credentialsApi.accept(wallet, newCredentialId)
            credentialsApi.delete(wallet, newCredentialId)
            credentialsApi.restore(wallet, newCredentialId)
            credentialsApi.status(wallet, newCredentialId)
            categoryApi.add(wallet, categoryName)
            categoryApi.add(wallet, categoryNewName)
            credentialsApi.attachCategory(wallet, newCredentialId, categoryName, categoryNewName)
            credentialsApi.detachCategory(wallet, newCredentialId, categoryName, categoryNewName)
//            credentialsApi.reject(wallet, newCredentialId)
//            credentialsApi.delete(wallet, newCredentialId, true)
            //endregion -Credentials-

            //region -Verifier / request url-
                val sessionApi = Verifier.SessionApi(client)
                val verificationApi = Verifier.VerificationApi(client)
                val verificationUrl: String = verificationApi.verify(simplePresentationRequestPayload)
                val verificationId: String = Url(verificationUrl).parameters.getOrFail("state")
                //endregion -Verifier / request url-

                //region -Exchange / presentation-
                val resolvedPresentationOfferString: String = exchangeApi.resolvePresentationRequest(verificationUrl)
                val presentationDefinition = Url(resolvedPresentationOfferString).parameters.getOrFail("presentation_definition")

                var presentationSession = sessionApi.get(verificationId)
                assert(presentationSession.presentationDefinition == PresentationDefinition.fromJSONString(presentationDefinition))

                exchangeApi.matchCredentialsForPresentationDefinition(wallet, presentationDefinition, listOf(newCredentialId)
            )
                exchangeApi.unmatchedCredentialsForPresentationDefinition(wallet,presentationDefinition)
                exchangeApi.usePresentationRequest(
                    wallet, UsePresentationRequest(
                        did , resolvedPresentationOfferString,
                         listOf(newCredentialId))
                )

                presentationSession = sessionApi.get(verificationId)
                assert(presentationSession.tokenResponse?.vpToken?.jsonPrimitive?.contentOrNull?.expectLooksLikeJwt() != null) { "Received no valid token response!" }
                assert(presentationSession.tokenResponse?.presentationSubmission != null) { "should have a presentation submission after submission" }

                assert(presentationSession.verificationResult == true) { "overall verification should be valid" }
                presentationSession.policyResults.let {
                    require(it != null) { "policyResults should be available after running policies" }
                    assert(it.size > 1) { "no policies have run" }
                }
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

            //endregion -Exchange / presentation-

            //region -History-
            val historyApi = HistoryApi(client)
            historyApi.list(wallet) {
                assert(it.size >= 2) { "missing history items" }
                assert(it.any { it.operation == "useOfferRequest" } && it.any { it.operation == "usePresentationRequest" }) { "incorrect history items" }
            }
            //endregion -History-
            val sdJwtTest = E2ESdJwtTest(issuerApi, exchangeApi, sessionApi, verificationApi)
            //cleanup credentials
            credentialsApi.delete(wallet, newCredentialId)
            sdJwtTest.e2e(wallet, did)

            // Test Authorization Code flow with available authentication methods in Issuer API
            val authorizationCodeFlow = AuthorizationCodeFlow(testHttpClient(doFollowRedirects = false))
            authorizationCodeFlow.testIssuerAPI()

            // test External Signature API Endpoints
            ExchangeExternalSignatures().executeTestCases()
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

        suspend fun setupTestWallet(): LspPotentialWallet {
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

    fun String.expectLooksLikeJwt(): String =
        also { assert(startsWith("ey") && count { it == '.' } == 2) { "Does not look like JWT" } }


    val expectSuccess: suspend HttpResponse.() -> HttpResponse = {
        assert(this.status.isSuccess()) { "HTTP status is non-successful for response: $this, body is ${this.bodyAsText()}" }; this
    }

    val expectRedirect: HttpResponse.() -> HttpResponse = {
        assert(this.status == HttpStatusCode.Found) { "HTTP status is non-successful" }; this
    }

    val expectFailure: HttpResponse.() -> HttpResponse = {
        assert(!status.isSuccess()) { "HTTP status is successful" }; this
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
}