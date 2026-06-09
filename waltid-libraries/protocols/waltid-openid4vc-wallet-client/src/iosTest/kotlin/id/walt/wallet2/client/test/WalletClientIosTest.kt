package id.walt.wallet2.client.test

import id.walt.wallet2.client.MobileWalletClientFactory
import id.walt.wallet2.client.MobileWalletConfig
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
 * TODO: These tests compile and link successfully but fail at runtime due to iOS Keychain
 *       entitlement requirements. Kotlin/Native test binaries don't have keychain access
 *       entitlements, preventing iOS Keychain operations. Possible solutions:
 *       1. Run tests via XCTest wrapper with proper entitlements
 *       2. Create in-memory key provider for test-only usage
 *       3. Use expect/actual to swap real keychain with mock in tests
 *
 * Requires: iOS simulator (macOS CI runner) + keychain entitlements (not currently available in KN tests).
 */
class WalletClientIosTest {

    companion object {
        private const val TEST_WALLET_ID = "ios-test-wallet"
    }

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

    @Test
    fun credentialPersistsAcrossClientRecreation() = runTest {
        val walletConfig = MobileWalletConfig(
            walletId = TEST_WALLET_ID,
            onEvent = { event -> println("WALLET EVENT: $event") },
        )

        val client1 = MobileWalletClientFactory().create(walletConfig)
        val bootstrapResult = client1.bootstrap()

        val offer = EudiTestBackend.generateOffer()
        client1.receive(offer.offerUrl, txCode = offer.txCode)

        val client2 = MobileWalletClientFactory().create(walletConfig)
        val credentials = client2.credentials()
        assertTrue(credentials.isNotEmpty(), "Credentials should persist across client recreation")

        val credentialId = EudiTestBackend.extractCredentialIdFromOfferUrl(offer.offerUrl)
        val transaction = EudiTestBackend.createVerifierTransaction(credentialId)
        val presentResult = client2.present(transaction.authorizationRequestUri, did = bootstrapResult.did)
        assertTrue(presentResult.success, "Should present from persisted credentials: credentials=$credentials, result=$presentResult")

        EudiTestBackend.waitForVerifierSuccess(transaction.transactionId)
    }
}
