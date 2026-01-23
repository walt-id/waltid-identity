@file:OptIn(ExperimentalUuidApi::class)

import id.walt.commons.ServiceConfiguration
import id.walt.commons.featureflag.CommonsFeatureCatalog
import id.walt.commons.testing.E2ETest
import id.walt.commons.testing.utils.ServiceTestUtils.loadResource
import id.walt.did.dids.DidService
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.issuer.issuerModule
import id.walt.oid4vc.OpenID4VCIVersion
import id.walt.test.integration.environment.api.issuer.IssuerApi
import id.walt.test.integration.environment.api.verifier.Verifier
import id.walt.test.integration.environment.api.wallet.CredentialsApi
import id.walt.test.integration.environment.api.wallet.DidsApi
import id.walt.test.integration.environment.api.wallet.ExchangeApi
import id.walt.test.integration.tests.AbstractIntegrationTest
import id.walt.verifier.verifierModule
import id.walt.webwallet.service.issuers.IssuersService
import id.walt.webwallet.usecase.issuer.IssuerUseCaseImpl
import id.walt.webwallet.webWalletModule
import io.klogging.Klogging
import io.ktor.server.application.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Deprecated(
    "Old Testcase Style: lock at id.walt.test.integration.tests.IssueSdJwtCredentialIntegrationTest to" +
            "see how integration tests should be written"
)
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

        fun testHttpClient(token: String? = null, doFollowRedirects: Boolean = true) =
            environment.testHttpClient(token, doFollowRedirects)
    }

    val e2eTestModule: Application.() -> Unit = {
        webWalletModule(true)
        issuerModule(false)
        verifierModule(false)
    }

    @BeforeEach
    fun before() = runBlocking {
        logger.info { "Before Each" }
    }


    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun e2e() = runTest {
        //TODO:
        // All tests here should be ported to JUnit tests. Example: id.walt.test.integration.tests.IssueSdJwtCredentialIntegrationTest
        logger.error { "*************************** RUNNING TESTS **************************************" }
        val e2e = environment.e2e
        val walletApi = defaultWalletApi
        val wallet = walletApi.getWallet()

        var client = walletApi.httpClient
        val issuerApi = environment.getIssuerApi()
        val exchangeApi = walletApi.exchangeApi
        val sessionApi = Verifier.SessionApi(e2e, client)
        val verificationApi = Verifier.VerificationApi(e2e, client)

        // ---------------------------------------------------------------
        /*
        val defaultDid = walletApi.getDefaultDid()
        val did = defaultDid.did
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
*/
        //endregion -Exchange / presentation-

        //region -History-
        /*
        val historyApi = HistoryApi(e2e, client)
        historyApi.list(wallet.id) {
            assertTrue(it.size >= 2, "missing history items")
            assertTrue(
                it.any { it.operation == "useOfferRequest" } && it.any { it.operation == "usePresentationRequest" },
                "incorrect history items"
            )
        }
         */
        //endregion -History-

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
        //TODO:
        //ExchangeExternalSignatures(e2e).executeTestCases()
        //endregion -External Signatures-

        //region -Input Descriptor Matching (Wallet)-
        val inputDescTest = InputDescriptorMatchingTest(issuerApi, exchangeApi, sessionApi, verificationApi)
        inputDescTest.e2e(wallet.id, walletApi.getDefaultDid().did)
        //endregion -Input Descriptor Matching (Wallet)-

        //region -Presentation Definition Policy (id.walt.test.integration.environment.api.verifier.Verifier)-
        PresentationDefinitionPolicyTests(e2e).runTests()
        //endregion -Presentation Definition Policy (id.walt.test.integration.environment.api.verifier.Verifier)-

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
            DidService.minimalInit()
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
        // ExchangeExternalSignatures(this).executeTestCases()
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
