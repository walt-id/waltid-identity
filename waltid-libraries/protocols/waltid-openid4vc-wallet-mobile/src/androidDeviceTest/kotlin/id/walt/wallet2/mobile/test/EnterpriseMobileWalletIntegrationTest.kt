package id.walt.wallet2.mobile.test

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import id.walt.mobile.test.backend.EnterpriseMobileAttestationConfig
import id.walt.mobile.test.backend.EnterpriseMobileFixtureClient
import id.walt.mobile.test.backend.EnterpriseMobilePlatform
import id.walt.mobile.test.backend.EnterpriseMobileScenario
import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletFactory
import id.walt.wallet2.mobile.WalletAttestationConfig
import id.walt.webdatafetching.WebDataFetcherManager
import id.walt.webdatafetching.WebDataFetchingConfiguration
import id.walt.webdatafetching.config.HttpEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.util.UUID
import kotlin.test.assertTrue

class EnterpriseMobileWalletIntegrationTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun setupEngine() {
            WebDataFetcherManager.globalDefaultConfiguration =
                WebDataFetchingConfiguration(http = HttpEngine.OkHttp)
        }
    }

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val fixtureBaseUrl: String?
        get() = InstrumentationRegistry.getArguments().getString("enterprise_fixture_base_url")

    @Test
    fun receiveEnterpriseMdlFromEnterpriseIssuer2() = runBlocking {
        receiveCredentialFromEnterpriseIssuer2("enterprise-mdl")
    }

    @Test
    fun receiveEnterpriseMdlWithClientAttestationFromEnterpriseIssuer2() = runBlocking {
        receiveCredentialFromEnterpriseIssuer2("enterprise-mdl-client-attestation")
    }

    @Test
    fun receiveAndPresentEnterpriseMdlIssuer2Verifier2Flow() = runBlocking {
        receiveAndPresentEnterpriseCredential("enterprise-mdl")
    }

    @Test
    fun receiveAndPresentEnterpriseMdlWithClientAttestationIssuer2Verifier2Flow() = runBlocking {
        receiveAndPresentEnterpriseCredential("enterprise-mdl-client-attestation")
    }

    @Test
    fun enterpriseCredentialPersistsAcrossWalletRecreation() = runBlocking {
        val fixture = requireFixture()
        val scenario = enterpriseScenario(fixture, "enterprise-mdl")
        val walletId = "android-enterprise-persist-${scenario.id}-${UUID.randomUUID()}"
        val offer = fixture.createOffer(scenario, EnterpriseMobilePlatform.ANDROID)

        val wallet1 = createWallet(walletId, offer.attestation)
        val bootstrapResult = wallet1.bootstrap()
        wallet1.receive(offer.offerUrl, txCode = offer.txCode)

        val wallet2 = createWallet(walletId, offer.attestation)
        val credentials = wallet2.credentials()
        assertTrue(credentials.isNotEmpty(), "Enterprise credential should persist across wallet recreation")

        val session = fixture.createVerifierSession(scenario, EnterpriseMobilePlatform.ANDROID)
        val presentResult = wallet2.present(session.authorizationRequestUri, did = bootstrapResult.did)
        assertTrue(
            presentResult.success,
            "Should present persisted Enterprise credential for ${scenario.displayName}: credentials=$credentials, result=$presentResult",
        )
        fixture.waitForVerifierSuccess(session.sessionId)
    }

    private suspend fun receiveCredentialFromEnterpriseIssuer2(scenarioId: String) {
        val fixture = requireFixture()
        val scenario = enterpriseScenario(fixture, scenarioId)
        val offer = fixture.createOffer(scenario, EnterpriseMobilePlatform.ANDROID)
        val wallet = createWallet(
            walletId = "android-enterprise-receive-${scenario.id}-${UUID.randomUUID()}",
            attestation = offer.attestation,
        )
        wallet.bootstrap()

        val credentialIds = wallet.receive(offer.offerUrl, txCode = offer.txCode)

        assertTrue(
            credentialIds.isNotEmpty(),
            "Should receive ${scenario.displayName} from Enterprise issuer2",
        )
    }

    private suspend fun receiveAndPresentEnterpriseCredential(scenarioId: String) {
        val fixture = requireFixture()
        val scenario = enterpriseScenario(fixture, scenarioId)
        assertTrue(scenario.supportsPresentation, "${scenario.displayName} should support presentation")

        val offer = fixture.createOffer(scenario, EnterpriseMobilePlatform.ANDROID)
        val wallet = createWallet(
            walletId = "android-enterprise-present-${scenario.id}-${UUID.randomUUID()}",
            attestation = offer.attestation,
        )
        val bootstrapResult = wallet.bootstrap()

        val credentialIds = wallet.receive(offer.offerUrl, txCode = offer.txCode)
        assertTrue(credentialIds.isNotEmpty(), "Should receive ${scenario.displayName}")

        val credentials = wallet.credentials()
        assertTrue(credentials.isNotEmpty(), "Should have stored ${scenario.displayName} credentials")

        val session = fixture.createVerifierSession(scenario, EnterpriseMobilePlatform.ANDROID)
        val presentResult = wallet.present(session.authorizationRequestUri, did = bootstrapResult.did)
        assertTrue(
            presentResult.success,
            "Enterprise verifier2 presentation should succeed for ${scenario.displayName}: credentials=$credentials, result=$presentResult",
        )

        fixture.waitForVerifierSuccess(session.sessionId)
    }

    private fun requireFixture(): EnterpriseMobileFixtureClient {
        val baseUrl = fixtureBaseUrl
        assumeTrue("Set enterprise_fixture_base_url to run Enterprise mobile integration tests", !baseUrl.isNullOrBlank())
        return EnterpriseMobileFixtureClient(baseUrl!!)
    }

    private suspend fun enterpriseScenario(
        fixture: EnterpriseMobileFixtureClient,
        scenarioId: String,
    ): EnterpriseMobileScenario =
        fixture.scenarios().first { it.id == scenarioId }

    private fun createWallet(
        walletId: String,
        attestation: EnterpriseMobileAttestationConfig?,
    ) = MobileWalletFactory(context).create(
        MobileWalletConfig(
            walletId = walletId,
            attestationConfig = attestation?.toWalletAttestationConfig(),
            onEvent = { event -> println("WALLET EVENT: $event") },
        )
    )

    private fun EnterpriseMobileAttestationConfig.toWalletAttestationConfig() =
        WalletAttestationConfig(
            baseUrl = baseUrl,
            attesterPath = attesterPath,
            bearerToken = bearerToken,
            hostHeader = hostHeader,
        )
}
