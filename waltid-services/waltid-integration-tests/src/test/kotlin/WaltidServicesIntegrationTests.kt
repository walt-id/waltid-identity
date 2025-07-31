@file:OptIn(ExperimentalUuidApi::class)

import id.walt.commons.ServiceConfiguration
import id.walt.commons.featureflag.CommonsFeatureCatalog
import id.walt.commons.testing.E2ETest
import id.walt.commons.testing.utils.ServiceTestUtils.loadResource
import id.walt.issuer.feat.lspPotential.lspPotentialIssuanceTestApi
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.issuer.issuance.openapi.issuerapi.IssuanceExamples
import id.walt.issuer.issuerModule
import id.walt.oid4vc.OpenID4VCIVersion
import id.walt.oid4vc.data.OpenId4VPProfile
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.util.JwtUtils
import id.walt.test.integration.environment.api.wallet.DidsApi
import id.walt.test.integration.expectLooksLikeJwt
import id.walt.test.integration.expectSuccess
import id.walt.test.integration.tests.AbstractIntegrationTest
import id.walt.verifier.lspPotential.lspPotentialVerificationTestApi
import id.walt.verifier.verifierModule
import id.walt.w3c.schemes.JwsSignatureScheme
import id.walt.webwallet.db.models.AccountWalletListing
import id.walt.webwallet.service.issuers.IssuersService
import id.walt.webwallet.usecase.issuer.IssuerUseCaseImpl
import id.walt.webwallet.web.controllers.exchange.UsePresentationRequest
import id.walt.webwallet.web.model.AccountRequest
import id.walt.webwallet.web.model.EmailAccountRequest
import id.walt.webwallet.webWalletModule
import io.klogging.Klogging
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeEach
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class WaltidServicesIntegrationTests : AbstractIntegrationTest(), Klogging {

    companion object {
        val defaultTestTimeout = 5.minutes
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

        fun testHttpClient(token: String? = null, doFollowRedirects: Boolean = true) =
            environment.testHttpClient(token, doFollowRedirects)
    }

    val e2eTestModule: Application.() -> Unit = {
        webWalletModule(true)
        issuerModule(false)
        lspPotentialIssuanceTestApi()
        verifierModule(false)
        lspPotentialVerificationTestApi()
    }

    @BeforeEach
    fun before() = runBlocking {
        logger.info { "Before Each" }
    }


    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun e2e() = runTest {
        logger.error { "*************************** RUNNING TESTS **************************************" }
        val e2e = environment.e2e
        val walletApi = environment.getWalletApi().loginWithDefaultUser()
        var client = walletApi.httpClient
        val wallets = walletApi.listAccountWallets()
        val wallet = wallets.wallets.first()


        // the e2e http request tests here


        //region -Categories-
        val categoryApi = CategoryApi(e2e, client)
        val categoryName = "name#1"
        val categoryNewName = "name#2"
        categoryApi.list(wallet.id, 0)
        categoryApi.add(wallet.id, categoryName)
        categoryApi.list(wallet.id, 1) {
            assertNotNull(it.single { it["name"]?.jsonPrimitive?.content == categoryName })
        }
        categoryApi.rename(wallet.id, categoryName, categoryNewName)
        categoryApi.list(wallet.id, 1) {
            assertNotNull(it.single { it["name"]?.jsonPrimitive?.content == categoryNewName })
        }
        categoryApi.delete(wallet.id, categoryNewName)
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
        exchangeApi.resolveCredentialOffer(wallet.id, offerUrl)
        exchangeApi.useOfferRequest(wallet.id, offerUrl, 1) {
            val cred = it.first()
            assertContains(JwtUtils.parseJWTPayload(cred.document).keys, JwsSignatureScheme.JwsOption.VC)
            newCredentialId = cred.id
        }
        //endregion -Exchange / claim-

        //region -Credentials-
        val credentialsApi = CredentialsApi(e2e, client)
        credentialsApi.list(wallet.id, expectedSize = 1, expectedCredential = arrayOf(newCredentialId))
        credentialsApi.get(wallet.id, newCredentialId)
        credentialsApi.accept(wallet.id, newCredentialId)
        credentialsApi.delete(wallet.id, newCredentialId)
        credentialsApi.restore(wallet.id, newCredentialId)
        credentialsApi.status(wallet.id, newCredentialId)
        categoryApi.add(wallet.id, categoryName)
        categoryApi.add(wallet.id, categoryNewName)
        credentialsApi.attachCategory(wallet.id, newCredentialId, categoryName, categoryNewName)
        credentialsApi.detachCategory(wallet.id, newCredentialId, categoryName, categoryNewName)
//            credentialsApi.reject(wallet.id, newCredentialId)
//            credentialsApi.delete(wallet.id, newCredentialId, true)
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
        exchangeApi.resolvePresentationRequest(wallet.id, verificationUrl) {
            resolvedPresentationOfferString = it
            presentationDefinition = Url(it).parameters.getOrFail("presentation_definition")
        }

        sessionApi.get(verificationId) {
            assertTrue(it.presentationDefinition == PresentationDefinition.fromJSONString(presentationDefinition))
        }

        exchangeApi.matchCredentialsForPresentationDefinition(
            wallet.id, presentationDefinition, listOf(newCredentialId)
        )
        val defaultDid = walletApi.getDefaultDid(wallet.id)
        val did = defaultDid.did
        exchangeApi.unmatchedCredentialsForPresentationDefinition(wallet.id, presentationDefinition)
        exchangeApi.usePresentationRequest(
            wallet.id, UsePresentationRequest(did, resolvedPresentationOfferString, listOf(newCredentialId))
        )

        sessionApi.get(verificationId) {
            assertTrue(
                it.tokenResponse?.vpToken?.jsonPrimitive?.contentOrNull?.expectLooksLikeJwt() != null,
                "Received no valid token response!"
            )
            assertTrue(
                it.tokenResponse?.presentationSubmission != null,
                "should have a presentation submission after submission"
            )

            assertTrue(it.verificationResult == true, "overall verification should be valid")
            it.policyResults.let {
                require(it != null) { "policyResults should be available after running policies" }
                assertTrue(it.size > 1, "no policies have run")
            }
        }
        val lspPotentialIssuance = LspPotentialIssuance(e2e, environment.testHttpClient(doFollowRedirects = false))
        lspPotentialIssuance.testTrack1()
        lspPotentialIssuance.testTrack2()
        val lspPotentialVerification =
            LspPotentialVerification(e2e, environment.testHttpClient(doFollowRedirects = false))
        lspPotentialVerification.testPotentialInteropTrack3()
        lspPotentialVerification.testPotentialInteropTrack4()
        val lspPotentialWallet = setupTestWallet(e2e)
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
        historyApi.list(wallet.id) {
            assertTrue(it.size >= 2, "missing history items")
            assertTrue(
                it.any { it.operation == "useOfferRequest" } && it.any { it.operation == "usePresentationRequest" },
                "incorrect history items"
            )
        }
        //endregion -History-
        val sdJwtTest = E2ESdJwtTest(issuerApi, exchangeApi, sessionApi, verificationApi)
        //cleanup credentials
        credentialsApi.delete(wallet.id, newCredentialId)
        sdJwtTest.e2e(wallet.id, did)

        // Test Authorization Code flow with available authentication methods in Issuer API
        val authorizationCodeFlow = AuthorizationCodeFlow(e2e, environment.testHttpClient(doFollowRedirects = false))
        authorizationCodeFlow.testIssuerAPI()

        // Test Issuer Draft 11
        val draft11Issuer = Draft11(e2e, environment.testHttpClient(doFollowRedirects = false))

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
            Json.decodeFromString<IssuanceRequest>(loadResource("issuance/openbadgecredential-issuance-request.json"))
                .copy(
                    standardVersion = OpenID4VCIVersion.DRAFT11,
                )

        draft11.testIssuanceDraft11PreAuthFlow(preAuthFlowIssuanceReq, wallet.id)

        val preAuthFlowIssuanceReqOfferedCredByValue = preAuthFlowIssuanceReq.copy(
            standardVersion = OpenID4VCIVersion.DRAFT11,
            draft11EncodeOfferedCredentialsByReference = false,
        )

        draft11.testIssuanceDraft11PreAuthFlow(preAuthFlowIssuanceReqOfferedCredByValue, wallet.id)


        // Test External Signature API Endpoints
        //In the context of these test cases, a new wallet is created and initialized
        //accordingly, i.e., the default wallet that is employed by all the other test
        //cases is not used here.
        //region -External Signatures-
        ExchangeExternalSignatures(e2e).executeTestCases()
        //endregion -External Signatures-

        //region -Input Descriptor Matching (Wallet)-
        val inputDescTest = InputDescriptorMatchingTest(issuerApi, exchangeApi, sessionApi, verificationApi)
        //cleanup credentials
        credentialsApi.delete(wallet.id, newCredentialId)
        inputDescTest.e2e(wallet.id, did)
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
            wallet = wallet.id
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
        val e2e = E2ETest()
        TODO("Implement e2e test")
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
        var authApi = environment.getWalletApi().loginWithDefaultUser()

        // the e2e http request tests here

        //region -Auth-
        TODO("Implement Junit Test")
        val e2e = E2ETest()
        //region -Dids-
        val didsApi = DidsApi(e2e, client)
        lateinit var did: String
        didsApi.list(wallet, DidsApi.DefaultDidOption.Any, 1) {
            assertTrue(it.first().default)
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
        var client = environment.testHttpClient()
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

    suspend fun setupTestWallet(e2e: E2ETest): LspPotentialWallet {
        var client = environment.testHttpClient()
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

                client = environment.testHttpClient(token = token)
            }
        }
        val walletId = client.get("/wallet-api/wallet/accounts/wallets").expectSuccess()
            .body<AccountWalletListing>().wallets.first().id.toString()
        return LspPotentialWallet(e2e, client, walletId)
    }
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
