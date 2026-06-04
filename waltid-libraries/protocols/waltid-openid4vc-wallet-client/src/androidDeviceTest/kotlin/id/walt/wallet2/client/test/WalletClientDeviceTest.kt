package id.walt.wallet2.client.test

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import id.walt.wallet2.client.MobileWalletClientFactory
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Android instrumented test for the wallet-client library.
 *
 * Exercises the full mobile stack: AndroidKeyStore crypto + SQLDelight persistence
 * + OID4VCI/VP protocol against the public EUDI test backend.
 *
 * Requires: Android emulator or device with API 30+.
 */
class WalletClientDeviceTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun bootstrapCreatesKeyAndDid() = runTest {
        val client = MobileWalletClientFactory(context).create()
        val result = client.bootstrap()
        assertNotNull(result.keyId, "bootstrap should create a key")
        assertNotNull(result.did, "bootstrap should create a DID")
        assertTrue(result.did!!.startsWith("did:"), "DID should start with 'did:'")
    }

    @Test
    fun receiveCredentialFromEudi() = runTest {
        val client = MobileWalletClientFactory(context).create()
        client.bootstrap()

        val offer = EudiTestBackend.generateOffer()
        val credentialIds = client.receive(offer.offerUrl, txCode = offer.txCode)
        assertTrue(credentialIds.isNotEmpty(), "Should receive at least one credential")
    }

    @Test
    fun receiveAndPresentFullFlow() = runTest {
        val client = MobileWalletClientFactory(context).create()
        val bootstrapResult = client.bootstrap()

        // Receive credential from EUDI issuer
        val offer = EudiTestBackend.generateOffer()
        val credentialIds = client.receive(offer.offerUrl, txCode = offer.txCode)
        assertTrue(credentialIds.isNotEmpty(), "Should receive at least one credential")

        // Verify credential is stored
        val credentials = client.credentials()
        assertTrue(credentials.isNotEmpty(), "Should have stored credentials")

        // Present to EUDI verifier
        val transaction = EudiTestBackend.createVerifierTransaction()
        val presentResult = client.present(transaction.authorizationRequestUri, did = bootstrapResult.did)
        assertTrue(presentResult.success, "Presentation should succeed")

        // Verify verifier received the presentation
        EudiTestBackend.waitForVerifierSuccess(transaction.transactionId)
    }

    @Test
    fun credentialPersistsAcrossClientRecreation() = runTest {
        val client1 = MobileWalletClientFactory(context).create()
        val bootstrapResult = client1.bootstrap()

        // Receive with first client instance
        val offer = EudiTestBackend.generateOffer()
        client1.receive(offer.offerUrl, txCode = offer.txCode)

        // Create new client instance (simulates app restart — same database file)
        val client2 = MobileWalletClientFactory(context).create()
        val credentials = client2.credentials()
        assertTrue(credentials.isNotEmpty(), "Credentials should persist across client recreation")

        // Present from persisted state
        val transaction = EudiTestBackend.createVerifierTransaction()
        val presentResult = client2.present(transaction.authorizationRequestUri, did = bootstrapResult.did)
        assertTrue(presentResult.success, "Should present from persisted credentials")
        EudiTestBackend.waitForVerifierSuccess(transaction.transactionId)
    }
}
