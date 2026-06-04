package id.walt.wallet2.client.test

import id.walt.wallet2.client.MobileWalletClientFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * iOS simulator test for the wallet-client library.
 *
 * Exercises the full mobile stack: iOS Keychain crypto + SQLDelight native persistence
 * + OID4VCI/VP protocol against the public EUDI test backend.
 *
 * Requires: iOS simulator (macOS CI runner).
 */
class WalletClientIosTest {

    @Test
    fun bootstrapCreatesKeyAndDid() = runTest {
        val client = MobileWalletClientFactory().create()
        val result = client.bootstrap()
        assertNotNull(result.keyId, "bootstrap should create a key")
        assertNotNull(result.did, "bootstrap should create a DID")
        assertTrue(result.did!!.startsWith("did:"), "DID should start with 'did:'")
    }

    @Test
    fun receiveCredentialFromEudi() = runTest {
        val client = MobileWalletClientFactory().create()
        client.bootstrap()

        val offer = EudiTestBackend.generateOffer()
        val credentialIds = client.receive(offer.offerUrl, txCode = offer.txCode)
        assertTrue(credentialIds.isNotEmpty(), "Should receive at least one credential")
    }

    @Test
    fun receiveAndPresentFullFlow() = runTest {
        val client = MobileWalletClientFactory().create()
        val bootstrapResult = client.bootstrap()

        val offer = EudiTestBackend.generateOffer()
        val credentialIds = client.receive(offer.offerUrl, txCode = offer.txCode)
        assertTrue(credentialIds.isNotEmpty(), "Should receive at least one credential")

        val credentials = client.credentials()
        assertTrue(credentials.isNotEmpty(), "Should have stored credentials")

        val transaction = EudiTestBackend.createVerifierTransaction()
        val presentResult = client.present(transaction.authorizationRequestUri, did = bootstrapResult.did)
        assertTrue(presentResult.success, "Presentation should succeed")

        EudiTestBackend.waitForVerifierSuccess(transaction.transactionId)
    }
}
