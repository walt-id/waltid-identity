@file:OptIn(ExperimentalUuidApi::class)

import id.walt.commons.ServiceConfiguration
import id.walt.commons.config.ConfigManager
import id.walt.commons.featureflag.CommonsFeatureCatalog
import id.walt.commons.testing.E2ETest
import id.walt.commons.testing.utils.ServiceTestUtils.loadResource
import id.walt.commons.web.plugins.httpJson
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyType
import id.walt.issuer.feat.lspPotential.lspPotentialIssuanceTestApi
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.issuer.issuance.openapi.issuerapi.IssuanceExamples
import id.walt.issuer.issuerModule
import id.walt.oid4vc.OpenID4VCIVersion
import id.walt.oid4vc.data.OpenId4VPProfile
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.util.JwtUtils
import id.walt.verifier.lspPotential.lspPotentialVerificationTestApi
import id.walt.verifier.verifierModule
import id.walt.w3c.schemes.JwsSignatureScheme
import id.walt.webwallet.config.RegistrationDefaultsConfig
import id.walt.webwallet.db.models.AccountWalletListing
import id.walt.webwallet.service.issuers.IssuersService
import id.walt.webwallet.usecase.issuer.IssuerUseCaseImpl
import id.walt.webwallet.web.controllers.exchange.UsePresentationRequest
import id.walt.webwallet.web.model.AccountRequest
import id.walt.webwallet.web.model.EmailAccountRequest
import id.walt.webwallet.webWalletModule
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
import io.ktor.server.application.*
import io.ktor.server.util.*
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class WaltidServicesE2ETests {

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

    val e2eTestModule: Application.() -> Unit = {
        webWalletModule(true)
        issuerModule(false)
        lspPotentialIssuanceTestApi()
        verifierModule(false)
        lspPotentialVerificationTestApi()
    }

    val e2e = E2ETest()

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun e2e() = e2e.testBlock(
        config = ServiceConfiguration("e2e-test"),
        features = listOf(
            id.walt.issuer.FeatureCatalog,
            id.walt.verifier.FeatureCatalog,
            id.walt.webwallet.FeatureCatalog
        ),
        featureAmendments = mapOf(
            CommonsFeatureCatalog.authenticationServiceFeature to id.walt.webwallet.web.plugins.walletAuthenticationPluginAmendment,
            // CommonsFeatureCatalog.authenticationServiceFeature to issuerAuthenticationPluginAmendment
        ),
        init = {
            id.walt.webwallet.webWalletSetup()
            id.walt.did.helpers.WaltidServices.minimalInit()
            id.walt.webwallet.db.Db.start()
        },
        module = e2eTestModule,
        timeout = defaultTestTimeout
    ) {
        var client = testHttpClient()
        lateinit var accountId: Uuid
        lateinit var wallet: Uuid
        var authApi = AuthApi(this, client)

        // the e2e http request tests here

        //region -Auth-
        authApi.apply {
            userInfo(HttpStatusCode.Unauthorized)
            e2e.test("/wallet-api/auth/login - wallet-api login") {
                val loginResult = login(defaultEmailAccount)
                client = testHttpClient(token = loginResult["token"]!!.jsonPrimitive.content)
                authApi = AuthApi(e2e, client)
            }
        }

        authApi.apply {
            userInfo(HttpStatusCode.OK) {
                accountId = it.id
            }
            userSession()
            userWallets(accountId) {
                wallet = it.wallets.first().id
                println("Selected wallet: $wallet")
            }
        }


        //region -Auth X5c-
        AuthApi.X5c(e2e, client).executeTestCases()
        //endregion -Auth X5c-
        //endregion -Auth-

        //region -Keys-
        val keysApi = KeysApi(e2e, client)
        val defaultKeyConfig = ConfigManager.getConfig<RegistrationDefaultsConfig>().defaultKeyConfig
        // requires registration-defaults to not be disabled in _features.confval defaultKeyConfig = ConfigManager.getConfig<RegistrationDefaultsConfig>().defaultKeyConfig
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

        //region -Dids-
        val didsApi = DidsApi(e2e, client)
        lateinit var did: String
        val createdDids = mutableListOf<String>()
        didsApi.list(wallet, DidsApi.DefaultDidOption.Any, 1) {
            assert(it.first().default)
            did = it.first().did
        }
        //todo: test for optional registration defaults
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
        /* Flaky test - sometimes works fine, sometimes responds with 400:
        didsApi.create(
            wallet, DidsApi.DidCreateRequest(method = "cheqd", options = mapOf("network" to "testnet"))
        ) {
            createdDids.add(it)
        }*/

        //TODO: error(400) DID method not supported for auto-configuration: ebsi
//            didsApi.create(wallet, DidsApi.DidCreateRequest(method = "ebsi", options = mapOf("version" to 2, "bearerToken" to "token"))){
//                createdDids.add(it)
//            }

        //TODO: didsApi.create(wallet, DidsApi.DidCreateRequest(method = "iota")){ createdDids.add(it) }
        didsApi.default(wallet, createdDids[0])
        didsApi.list(wallet, DidsApi.DefaultDidOption.Some(createdDids[0]), createdDids.size + 1)
        for (d in createdDids) {
            didsApi.delete(wallet, d)
        }
        didsApi.list(wallet, DidsApi.DefaultDidOption.None, 1)
        didsApi.get(wallet, did)
        didsApi.default(wallet, did)
        didsApi.list(wallet, DidsApi.DefaultDidOption.Some(did), 1)
        //endregion -Dids-

        //region -Categories-
        val categoryApi = CategoryApi(this, client)
        val categoryName = "name#1"
        val categoryNewName = "name#2"
        categoryApi.list(wallet, 0)
        categoryApi.add(wallet, categoryName)
        categoryApi.list(wallet, 1) {
            assertNotNull(it.single { it["name"]?.jsonPrimitive?.content == categoryName })
        }
        categoryApi.rename(wallet, categoryName, categoryNewName)
        categoryApi.list(wallet, 1) {
            assertNotNull(it.single { it["name"]?.jsonPrimitive?.content == categoryNewName })
        }
        categoryApi.delete(wallet, categoryNewName)
        //endregion -Categories

        //region -Issuer / offer url-
        lateinit var offerUrl: String
        val issuerApi = IssuerApi(
            e2e, client,
            // uncomment the following line, to test status callbacks, update webhook id as required.
            //    "https://webhook.site/d879094b-2275-4ae7-b1c5-ebfb9f08dfdb"
        )
        val issuanceRequest = Json.decodeFromJsonElement<IssuanceRequest>(jwtCredential)
        println("issuance-request:")
        println(issuanceRequest)
        issuerApi.jwt(issuanceRequest) {
            offerUrl = it
            println("offer: $offerUrl")
        }
        assertTrue(offerUrl.contains("draft13"))
        assertFalse(offerUrl.contains("draft11"))

        //endregion -Issuer / offer url-

        //region -Exchange / claim-
        val exchangeApi = ExchangeApi(e2e, client)
        lateinit var newCredentialId: String
        exchangeApi.resolveCredentialOffer(wallet, offerUrl)
        exchangeApi.useOfferRequest(wallet, offerUrl, 1) {
            val cred = it.first()
            assertContains(JwtUtils.parseJWTPayload(cred.document).keys, JwsSignatureScheme.JwsOption.VC)
            newCredentialId = cred.id
        }
        //endregion -Exchange / claim-

        //region -Credentials-
        val credentialsApi = CredentialsApi(e2e, client)
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
        lateinit var verificationUrl: String
        lateinit var verificationId: String
        val sessionApi = Verifier.SessionApi(e2e, client)
        val verificationApi = Verifier.VerificationApi(e2e, client)
        verificationApi.verify(simplePresentationRequestPayload) {
            verificationUrl = it
            verificationId = Url(verificationUrl).parameters.getOrFail("state")
        }
        //endregion -Verifier / request url-

        //region -Exchange / presentation-
        lateinit var resolvedPresentationOfferString: String
        lateinit var presentationDefinition: String
        exchangeApi.resolvePresentationRequest(wallet, verificationUrl) {
            resolvedPresentationOfferString = it
            presentationDefinition = Url(it).parameters.getOrFail("presentation_definition")
        }

        sessionApi.get(verificationId) {
            assert(it.presentationDefinition == PresentationDefinition.fromJSONString(presentationDefinition))
        }

        exchangeApi.matchCredentialsForPresentationDefinition(
            wallet, presentationDefinition, listOf(newCredentialId)
        )
        exchangeApi.unmatchedCredentialsForPresentationDefinition(wallet, presentationDefinition)
        exchangeApi.usePresentationRequest(
            wallet, UsePresentationRequest(did, resolvedPresentationOfferString, listOf(newCredentialId))
        )

        sessionApi.get(verificationId) {
            assert(it.tokenResponse?.vpToken?.jsonPrimitive?.contentOrNull?.expectLooksLikeJwt() != null) { "Received no valid token response!" }
            assert(it.tokenResponse?.presentationSubmission != null) { "should have a presentation submission after submission" }

            assert(it.verificationResult == true) { "overall verification should be valid" }
            it.policyResults.let {
                require(it != null) { "policyResults should be available after running policies" }
                assert(it.size > 1) { "no policies have run" }
            }
        }
        val lspPotentialIssuance = LspPotentialIssuance(e2e, testHttpClient(doFollowRedirects = false))
        lspPotentialIssuance.testTrack1()
        lspPotentialIssuance.testTrack2()
        val lspPotentialVerification = LspPotentialVerification(e2e, testHttpClient(doFollowRedirects = false))
        lspPotentialVerification.testPotentialInteropTrack3()
        lspPotentialVerification.testPotentialInteropTrack4()
        val lspPotentialWallet = setupTestWallet()
        lspPotentialWallet.testMDocIssuance(IssuanceExamples.mDLCredentialIssuanceData, true)
        lspPotentialWallet.testMDocIssuance(IssuanceExamples.mDLCredentialIssuanceDataJwtProof, false)
        lspPotentialWallet.testMdocPresentation()
        lspPotentialWallet.testSDJwtVCIssuance()
        lspPotentialWallet.testSDJwtPresentation(OpenId4VPProfile.HAIP)
        lspPotentialWallet.testSDJwtPresentation(OpenId4VPProfile.DEFAULT)
        lspPotentialWallet.testSDJwtVCIssuanceByIssuerDid()
        lspPotentialWallet.testSDJwtPresentation(OpenId4VPProfile.DEFAULT)
        lspPotentialWallet.testPresentationDefinitionCredentialMatching()

        //endregion -Exchange / presentation-

        //region -History-
        val historyApi = HistoryApi(e2e, client)
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
        val authorizationCodeFlow = AuthorizationCodeFlow(e2e, testHttpClient(doFollowRedirects = false))
        authorizationCodeFlow.testIssuerAPI()

        // Test Issuer Draft 11
        val draft11Issuer = Draft11(e2e, testHttpClient(doFollowRedirects = false))

        val idTokenIssuanceReq =
            Json.decodeFromString<IssuanceRequest>(loadResource("issuance/openbadgecredential-issuance-request-with-authorization-code-flow-and-id-token.json"))
                .copy(
                    credentialConfigurationId = "OpenBadgeCredential_jwt_vc",
                    standardVersion = OpenID4VCIVersion.DRAFT11,
                    useJar = true
                )

        draft11Issuer.testIssuerAPIDraft11AuthFlowWithJar(idTokenIssuanceReq)

        val vpTokenIssuanceReq =
            Json.decodeFromString<IssuanceRequest>(loadResource("issuance/openbadgecredential-issuance-request-with-authorization-code-flow-and-vp-token.json"))
                .copy(
                    credentialConfigurationId = "OpenBadgeCredential_jwt_vc",
                    standardVersion = OpenID4VCIVersion.DRAFT11,
                    useJar = true
                )

        draft11Issuer.testIssuerAPIDraft11AuthFlowWithJar(vpTokenIssuanceReq)

        val draft11 = Draft11(e2e, client)

        val preAuthFlowIssuanceReq =
            Json.decodeFromString<IssuanceRequest>(loadResource("issuance/openbadgecredential-issuance-request.json")).copy(
                standardVersion = OpenID4VCIVersion.DRAFT11,
            )

        draft11.testIssuanceDraft11PreAuthFlow(preAuthFlowIssuanceReq, wallet)

        val preAuthFlowIssuanceReqOfferedCredByValue = preAuthFlowIssuanceReq.copy(
            standardVersion = OpenID4VCIVersion.DRAFT11,
            draft11EncodeOfferedCredentialsByReference = false,
        )

        draft11.testIssuanceDraft11PreAuthFlow(preAuthFlowIssuanceReqOfferedCredByValue, wallet)


        // Test External Signature API Endpoints
        //In the context of these test cases, a new wallet is created and initialized
        //accordingly, i.e., the default wallet that is employed by all the other test
        //cases is not used here.
        //region -External Signatures-
        ExchangeExternalSignatures(this).executeTestCases()
        //endregion -External Signatures-

        //region -Input Descriptor Matching (Wallet)-
        val inputDescTest = InputDescriptorMatchingTest(issuerApi, exchangeApi, sessionApi, verificationApi)
        //cleanup credentials
        credentialsApi.delete(wallet, newCredentialId)
        inputDescTest.e2e(wallet, did)
        //endregion -Input Descriptor Matching (Wallet)-

        //region -Presentation Definition Policy (Verifier)-
        PresentationDefinitionPolicyTests(e2e).runTests()
        //endregion -Presentation Definition Policy (Verifier)-

        //region -ISO mDL Onboarding Service (Issuer)-
        IssuerIsoMdlOnboardingServiceTests(e2e).runTests()
        //endregion -ISO mDL Onboarding Service (Issuer)-

        //region -MDoc Prepared/Ready Wallet Test Utility (Wallet)
        MDocPreparedWallet(e2e).testWalletSetup()
        //endregion -MDoc Prepared/Ready Wallet Test Utility (Wallet)

        //region -Presented Credentials Feature (Verifier)-
        VerifierPresentedCredentialsTests(e2e).runTests()
        //endregion -Presented Credentials Feature (Verifier)-

        //region -Batch Issuance Test Suite-
        val batchIssuance = BatchIssuance(
            e2e = e2e,
            client = client,
            wallet = wallet
        )
        batchIssuance.runTests()
        //endregion -Batch Issuance Test Suite-

    }

    /* @Test // enable to execute test selectively
    fun lspIssuanceTests() = testBlock(timeout = defaultTestTimeout) {
        val client = testHttpClient(doFollowRedirects = false)
        val lspPotentialIssuance = LspPotentialIssuance(client)
        lspPotentialIssuance.testTrack1()
        lspPotentialIssuance.testTrack2()
    }*/

    /* @Test
    fun lspVerifierTests() = testBlock(timeout = defaultTestTimeout) {
        val client = testHttpClient(doFollowRedirects = false)
        val lspPotentialVerification = LspPotentialVerification(client)
        lspPotentialVerification.testPotentialInteropTrack3()
        lspPotentialVerification.testPotentialInteropTrack4()
    }*/

    //        @Test
    fun e2ePresDefPolicyTests() = E2ETest().testBlock(
        config = ServiceConfiguration("e2e-pres-def-tests"),
        features = listOf(
            id.walt.issuer.FeatureCatalog,
            id.walt.verifier.FeatureCatalog,
            id.walt.webwallet.FeatureCatalog
        ),
        featureAmendments = mapOf(
            CommonsFeatureCatalog.authenticationServiceFeature to id.walt.webwallet.web.plugins.walletAuthenticationPluginAmendment,
            // CommonsFeatureCatalog.authenticationServiceFeature to issuerAuthenticationPluginAmendment
        ),
        init = {
            id.walt.webwallet.webWalletSetup()
            id.walt.did.helpers.WaltidServices.minimalInit()
            id.walt.webwallet.db.Db.start()
        },
        module = e2eTestModule,
        timeout = defaultTestTimeout,
    ) {
        PresentationDefinitionPolicyTests(e2e).runTests()
    }

    //@Test
    fun testExternalSignatureAPIs() = E2ETest().testBlock(
        config = ServiceConfiguration("e2e-test"),
        features = listOf(
            id.walt.issuer.FeatureCatalog,
            id.walt.verifier.FeatureCatalog,
            id.walt.webwallet.FeatureCatalog
        ),
        featureAmendments = mapOf(
            CommonsFeatureCatalog.authenticationServiceFeature to id.walt.webwallet.web.plugins.walletAuthenticationPluginAmendment,
            // CommonsFeatureCatalog.authenticationServiceFeature to issuerAuthenticationPluginAmendment
        ),
        init = {
            id.walt.webwallet.webWalletSetup()
            id.walt.did.helpers.WaltidServices.minimalInit()
            id.walt.webwallet.db.Db.start()
        },
        module = e2eTestModule,
        timeout = defaultTestTimeout
    ) {
        ExchangeExternalSignatures(this).executeTestCases()
    }

    //@Test
    fun inputDescriptorTest() = E2ETest().testBlock(
        config = ServiceConfiguration("e2e-test"),
        features = listOf(
            id.walt.issuer.FeatureCatalog,
            id.walt.verifier.FeatureCatalog,
            id.walt.webwallet.FeatureCatalog
        ),
        featureAmendments = mapOf(
            CommonsFeatureCatalog.authenticationServiceFeature to id.walt.webwallet.web.plugins.walletAuthenticationPluginAmendment,
            // CommonsFeatureCatalog.authenticationServiceFeature to issuerAuthenticationPluginAmendment
        ),
        init = {
            id.walt.webwallet.webWalletSetup()
            id.walt.did.helpers.WaltidServices.minimalInit()
            id.walt.webwallet.db.Db.start()
        },
        module = e2eTestModule,
        timeout = defaultTestTimeout
    ) {
        var client = testHttpClient()
        lateinit var accountId: Uuid
        lateinit var wallet: Uuid
        var authApi = AuthApi(this, client)

        // the e2e http request tests here

        //region -Auth-

        authApi.apply {
            test("1. Auth - Login") {
                userInfo(HttpStatusCode.Unauthorized)
                val loginResult = login(defaultEmailAccount)
                client = testHttpClient(token = loginResult["token"]!!.jsonPrimitive.content)
                authApi = AuthApi(e2e, client)
            }
        }
        authApi.apply {
            userInfo(HttpStatusCode.OK) {
                accountId = it.id
            }
            userSession()
            userWallets(accountId) {
                wallet = it.wallets.first().id
                println("Selected wallet: $wallet")
            }
        }
        //region -Dids-
        val didsApi = DidsApi(e2e, client)
        lateinit var did: String
        didsApi.list(wallet, DidsApi.DefaultDidOption.Any, 1) {
            assert(it.first().default)
            did = it.first().did
        }

        val issuerApi = IssuerApi(e2e, client)
        val exchangeApi = ExchangeApi(e2e, client)
        val credentialsApi = CredentialsApi(e2e, client)
        val sessionApi = Verifier.SessionApi(e2e, client)
        val verificationApi = Verifier.VerificationApi(e2e, client)

        // Input descriptor matching test
        val inputDescTest = InputDescriptorMatchingTest(issuerApi, exchangeApi, sessionApi, verificationApi)

        inputDescTest.e2e(wallet, did)
    }

    //@Test
    suspend fun issuerCredentialsListTest() {
        var client = testHttpClient()
        assertFalse(
            IssuerUseCaseImpl(
                IssuersService,
                client
            ).fetchCredentials("https://issuer.portal.walt-test.cloud/draft11/.well-known/openid-credential-issuer")
                .isEmpty()
        )
        assertFalse(
            IssuerUseCaseImpl(
                IssuersService,
                client
            ).fetchCredentials("https://issuer.portal.walt-test.cloud/draft13/.well-known/openid-credential-issuer")
                .isEmpty()
        )
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
        return LspPotentialWallet(e2e, client, walletId)
    }

    /* @Test // enable to execute test selectively
    fun lspWalletTests() = E2ETest.testBlock(
        config = ServiceConfiguration("e2e-test"),
        features = listOf(id.walt.issuer.FeatureCatalog, id.walt.verifier.FeatureCatalog, id.walt.webwallet.FeatureCatalog),
        featureAmendments = mapOf(
            CommonsFeatureCatalog.authenticationServiceFeature to id.walt.webwallet.web.plugins.walletAuthenticationPluginAmendment,
            // CommonsFeatureCatalog.authenticationServiceFeature to issuerAuthenticationPluginAmendment
        ),
        init = {
            id.walt.webwallet.webWalletSetup()
            id.walt.did.helpers.WaltidServices.minimalInit()
            id.walt.webwallet.db.Db.start()
        },
        module = e2eTestModule,
        timeout = defaultTestTimeout
    ) {
        val lspPotentialWallet = setupTestWallet()
        lspPotentialWallet.testMDocIssuance()
        lspPotentialWallet.testMdocPresentation()

        lspPotentialWallet.testSDJwtVCIssuance()
        lspPotentialWallet.testSDJwtPresentation(OpenId4VPProfile.HAIP)
        lspPotentialWallet.testSDJwtPresentation(OpenId4VPProfile.DEFAULT)
        lspPotentialWallet.testPresentationDefinitionCredentialMatching()
    }*/

    /* @Test // enable to execute test selectively
    fun testSdJwtVCIssuanceWithIssuerDid() = testBlock(timeout = defaultTestTimeout) {
        val lspPotentialWallet = setupTestWallet()
        lspPotentialWallet.testSDJwtVCIssuanceByIssuerDid()
        lspPotentialWallet.testSDJwtPresentation(OpenId4VPProfile.DEFAULT)
    }*/
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
