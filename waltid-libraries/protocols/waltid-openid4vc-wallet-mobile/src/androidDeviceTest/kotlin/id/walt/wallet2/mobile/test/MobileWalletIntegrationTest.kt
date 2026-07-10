package id.walt.wallet2.mobile.test

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import id.walt.mobile.test.backend.DemoTestBackend
import id.walt.mobile.test.backend.EudiTestBackend
import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletFactory
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.UUID
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Android integration tests for the mobile wallet library.
 *
 * Exercises the full mobile stack: AndroidKeyStore crypto + SQLDelight persistence
 * + OID4VCI/VP protocol against the public EUDI test backend.
 *
 * Uses runBlocking (not runTest) because real network I/O requires real time
 * dispatchers — runTest's virtual time expires HTTP timeouts immediately.
 *
 * These are integration tests (not E2E UI tests) - they test the library directly
 * without UI automation. Run on every PR for fast feedback.
 */
class MobileWalletIntegrationTest {

    companion object {
        private const val TEST_WALLET_ID = "android-device-test-wallet"
    }

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun bootstrapCreatesKeyAndDid() = runBlocking {
        val client = MobileWalletFactory(context).create()
        val result = client.bootstrap()
        assertNotNull(result.keyId, "bootstrap should create a key")
        assertNotNull(result.did, "bootstrap should create a DID")
        assertTrue(result.did.startsWith("did:"), "DID should start with 'did:'")
    }

    @Test
    fun receiveCredentialFromEudi() = runBlocking {
        val client = MobileWalletFactory(context).create()
        client.bootstrap()

        val offer = EudiTestBackend.generateOffer()
        val credentialIds = client.receive(offer.offerUrl, txCode = offer.txCode)
        assertTrue(credentialIds.isNotEmpty(), "Should receive at least one credential")
    }

    @Test
    fun receiveEudiPidSdJwtFromDemoIssuer2() = runBlocking {
        receiveCredentialFromDemoIssuer2("eudi-pid-sdjwt")
    }

    @Test
    fun receiveEudiPidMdocFromDemoIssuer2() = runBlocking {
        receiveCredentialFromDemoIssuer2("eudi-pid-mdoc")
    }

    @Test
    fun receiveIsoMdlFromDemoIssuer2() = runBlocking {
        receiveCredentialFromDemoIssuer2("iso-mdl")
    }

    @Test
    fun receiveAndPresentFullFlow() = runBlocking {
        val client = MobileWalletFactory(context).create(
            MobileWalletConfig(onEvent = { event -> println("WALLET EVENT: $event") })
        )
        val bootstrapResult = client.bootstrap()

        val offer = EudiTestBackend.generateOffer()
        val credentialIds = client.receive(offer.offerUrl, txCode = offer.txCode)
        assertTrue(credentialIds.isNotEmpty(), "Should receive at least one credential")

        val credentials = client.credentials()
        assertTrue(credentials.isNotEmpty(), "Should have stored credentials")

        val credentialId = EudiTestBackend.extractCredentialIdFromOfferUrl(offer.offerUrl)
        val transaction = EudiTestBackend.createVerifierTransaction(credentialId)
        val presentResult = client.present(transaction.authorizationRequestUri, did = bootstrapResult.did)
        assertTrue(presentResult.success, "Presentation should succeed: credentials=$credentials, result=$presentResult")

        EudiTestBackend.waitForVerifierSuccess(transaction.transactionId)
    }

    @Test
    fun receiveAndPresentEudiPidSdJwtAgainstDemoIssuer2AndVerifier2() = runBlocking {
        receiveAndPresentDemoCredential("eudi-pid-sdjwt")
    }

    @Test
    fun receiveAndPresentEudiPidMdocAgainstDemoIssuer2AndVerifier2() = runBlocking {
        receiveAndPresentDemoCredential("eudi-pid-mdoc")
    }

    @Test
    fun previewAndSubmitEudiPidMdocAgainstDemoIssuer2AndVerifier2() = runBlocking {
        val scenario = demoPresentationScenario("eudi-pid-mdoc")
        val client = MobileWalletFactory(context).create(walletConfig("preview-submit-${scenario.id}"))
        val bootstrapResult = client.bootstrap()

        val offer = DemoTestBackend.createOffer(scenario)
        val credentialIds = client.receive(offer.offerUrl, txCode = offer.txCode)
        assertTrue(
            credentialIds.isNotEmpty(),
            "Should receive ${scenario.displayName} from public demo issuer2",
        )

        val session = DemoTestBackend.createVerifierSession(scenario)
        val preview = client.previewPresentation(session.authorizationRequestUri)
        assertTrue(
            preview.credentialOptions.isNotEmpty(),
            "Should preview at least one matching credential for ${scenario.displayName}: preview=$preview",
        )
        assertTrue(
            preview.credentialOptions.all { it.credentialId in credentialIds },
            "Preview should only offer credentials received in this test: received=$credentialIds, preview=$preview",
        )

        val result = client.submitPresentation(
            requestUrl = session.authorizationRequestUri,
            selectedCredentialIds = preview.credentialOptions.map { it.credentialId },
            did = bootstrapResult.did,
        )
        assertTrue(
            result.success,
            "public demo verifier2 stepwise presentation should succeed for ${scenario.displayName}: preview=$preview, result=$result",
        )

        DemoTestBackend.waitForVerifierSuccess(session.sessionId)
    }

    @Test
    fun receiveAndPresentIsoMdlAgainstDemoIssuer2AndVerifier2() = runBlocking {
        receiveAndPresentDemoCredential("iso-mdl")
    }

    @Test
    fun credentialPersistsAcrossWalletRecreation() = runBlocking {
        val walletConfig = MobileWalletConfig(
            walletId = TEST_WALLET_ID,
            onEvent = { event -> println("WALLET EVENT: $event") },
        )

        val client1 = MobileWalletFactory(context).create(walletConfig)
        val bootstrapResult = client1.bootstrap()

        val offer = EudiTestBackend.generateOffer()
        client1.receive(offer.offerUrl, txCode = offer.txCode)

        val client2 = MobileWalletFactory(context).create(walletConfig)
        val credentials = client2.credentials()
        assertTrue(credentials.isNotEmpty(), "Credentials should persist across client recreation")

        val credentialId = EudiTestBackend.extractCredentialIdFromOfferUrl(offer.offerUrl)
        val transaction = EudiTestBackend.createVerifierTransaction(credentialId)
        val presentResult = client2.present(transaction.authorizationRequestUri, did = bootstrapResult.did)
        assertTrue(presentResult.success, "Should present from persisted credentials: credentials=$credentials, result=$presentResult")
        EudiTestBackend.waitForVerifierSuccess(transaction.transactionId)
    }

    @Test
    fun demoCredentialPersistsAcrossWalletRecreation() = runBlocking {
        val scenario = DemoTestBackend.persistenceScenario
        val walletConfig = walletConfig("persist-${scenario.id}")

        val client1 = MobileWalletFactory(context).create(walletConfig)
        val bootstrapResult = client1.bootstrap()

        val offer = DemoTestBackend.createOffer(scenario)
        client1.receive(offer.offerUrl, txCode = offer.txCode)

        val client2 = MobileWalletFactory(context).create(walletConfig)
        val credentials = client2.credentials()
        assertTrue(credentials.isNotEmpty(), "public demo credential should persist across client recreation")

        val session = DemoTestBackend.createVerifierSession(scenario)
        val presentResult = client2.present(session.authorizationRequestUri, did = bootstrapResult.did)
        assertTrue(
            presentResult.success,
            "Should present persisted public demo credential for ${scenario.displayName}: credentials=$credentials, result=$presentResult",
        )
        DemoTestBackend.waitForVerifierSuccess(session.sessionId)
    }

    private fun walletConfig(prefix: String) = MobileWalletConfig(
        walletId = "android-demo-$prefix-${UUID.randomUUID()}",
        onEvent = { event -> println("WALLET EVENT: $event") },
    )

    private suspend fun receiveCredentialFromDemoIssuer2(scenarioId: String) {
        val scenario = demoScenario(scenarioId)
        val client = MobileWalletFactory(context).create(walletConfig("receive-${scenario.id}"))
        client.bootstrap()

        val offer = DemoTestBackend.createOffer(scenario)
        val credentialIds = client.receive(offer.offerUrl, txCode = offer.txCode)
        assertTrue(
            credentialIds.isNotEmpty(),
            "Should receive at least one ${scenario.displayName} credential from public demo issuer2",
        )
    }

    private suspend fun receiveAndPresentDemoCredential(scenarioId: String) {
        val scenario = demoPresentationScenario(scenarioId)
        val client = MobileWalletFactory(context).create(walletConfig("present-${scenario.id}"))
        val bootstrapResult = client.bootstrap()

        val offer = DemoTestBackend.createOffer(scenario)
        val credentialIds = client.receive(offer.offerUrl, txCode = offer.txCode)
        assertTrue(
            credentialIds.isNotEmpty(),
            "Should receive ${scenario.displayName} from public demo issuer2",
        )

        val credentials = client.credentials()
        assertTrue(credentials.isNotEmpty(), "Should have stored ${scenario.displayName} credentials")

        val session = DemoTestBackend.createVerifierSession(scenario)
        val presentResult = client.present(session.authorizationRequestUri, did = bootstrapResult.did)
        assertTrue(
            presentResult.success,
            "public demo verifier2 presentation should succeed for ${scenario.displayName}: credentials=$credentials, result=$presentResult",
        )

        DemoTestBackend.waitForVerifierSuccess(session.sessionId)
    }

    private fun demoScenario(id: String) = DemoTestBackend.scenarios.first { it.id == id }

    private fun demoPresentationScenario(id: String) = DemoTestBackend.presentationScenarios.first { it.id == id }
}
