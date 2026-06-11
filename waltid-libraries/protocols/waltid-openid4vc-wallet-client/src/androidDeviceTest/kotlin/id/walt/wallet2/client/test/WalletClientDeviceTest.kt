package id.walt.wallet2.client.test

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import id.walt.mobile.test.backend.EudiTestBackend
import id.walt.wallet2.client.MobileWalletConfig
import id.walt.wallet2.client.MobileWalletClientFactory
import id.walt.webdatafetching.WebDataFetcherManager
import id.walt.webdatafetching.WebDataFetchingConfiguration
import id.walt.webdatafetching.config.HttpEngine
import kotlinx.coroutines.runBlocking
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Android instrumented test for the wallet-client library.
 *
 * Exercises the full mobile stack: AndroidKeyStore crypto + SQLDelight persistence
 * + OID4VCI/VP protocol against the public EUDI test backend.
 *
 * Uses runBlocking (not runTest) because real network I/O requires real time
 * dispatchers — runTest's virtual time expires HTTP timeouts immediately.
 */
class WalletClientDeviceTest {

    companion object {
        private const val TEST_WALLET_ID = "android-device-test-wallet"

        @JvmStatic
        @BeforeClass
        fun setupEngine() {
            WebDataFetcherManager.globalDefaultConfiguration =
                WebDataFetchingConfiguration(http = HttpEngine.OkHttp)
        }
    }

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun bootstrapCreatesKeyAndDid() = runBlocking {
        val client = MobileWalletClientFactory(context).create()
        val result = client.bootstrap()
        assertNotNull(result.keyId, "bootstrap should create a key")
        assertNotNull(result.did, "bootstrap should create a DID")
        assertTrue(result.did!!.startsWith("did:"), "DID should start with 'did:'")
    }

    @Test
    fun receiveCredentialFromEudi() = runBlocking {
        val client = MobileWalletClientFactory(context).create()
        client.bootstrap()

        val offer = EudiTestBackend.generateOffer()
        val credentialIds = client.receive(offer.offerUrl, txCode = offer.txCode)
        assertTrue(credentialIds.isNotEmpty(), "Should receive at least one credential")
    }

    @Test
    fun receiveAndPresentFullFlow() = runBlocking {
        val client = MobileWalletClientFactory(context).create(
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
    fun credentialPersistsAcrossClientRecreation() = runBlocking {
        val walletConfig = MobileWalletConfig(
            walletId = TEST_WALLET_ID,
            onEvent = { event -> println("WALLET EVENT: $event") },
        )

        val client1 = MobileWalletClientFactory(context).create(walletConfig)
        val bootstrapResult = client1.bootstrap()

        val offer = EudiTestBackend.generateOffer()
        client1.receive(offer.offerUrl, txCode = offer.txCode)

        val client2 = MobileWalletClientFactory(context).create(walletConfig)
        val credentials = client2.credentials()
        assertTrue(credentials.isNotEmpty(), "Credentials should persist across client recreation")

        val credentialId = EudiTestBackend.extractCredentialIdFromOfferUrl(offer.offerUrl)
        val transaction = EudiTestBackend.createVerifierTransaction(credentialId)
        val presentResult = client2.present(transaction.authorizationRequestUri, did = bootstrapResult.did)
        assertTrue(presentResult.success, "Should present from persisted credentials: credentials=$credentials, result=$presentResult")
        EudiTestBackend.waitForVerifierSuccess(transaction.transactionId)
    }
}
