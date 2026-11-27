package id.walt.wallet2.mobile

import id.walt.wallet2.handlers.WalletIssuanceOutcome
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SigningRecoveryTest {
    @Test
    fun approveAfterReopeningUsesTheOriginalGrantAndKey() = runTest {
        val issuer = SigningRecoveryIssuer()
        val wallet = issuer.openWallet()
        val offer = wallet.startIssuance(MobileWalletIssuanceRequest(MobileWalletCredentialOffer.Uri(issuer.offer),
            clientId = "wallet", redirectUri = "wallet://callback", keyId = "holder"))
        val failure = assertIs<WalletIssuanceOutcome.Failed>(wallet.continuePreAuthorizedIssuance(offer.id))
        assertTrue(failure.error.canRetryOffer && failure.signingApprovalRequired)
        assertEquals(listOf(1, 1, 0, 0), listOf(issuer.exchanges, issuer.prompts, issuer.signatures, issuer.deliveries))
        wallet.close()

        val reopened = issuer.openWallet()
        val stored = assertIs<WalletIssuanceOutcome.Stored>(reopened.retryIssuanceSigning(offer.id))
        assertEquals(1, stored.credentialIds.size)
        assertEquals(stored.credentialIds, reopened.credentials().map { it.id })
        assertIs<WalletIssuanceOutcome.Failed>(reopened.retryIssuanceSigning(offer.id))
        assertEquals(listOf(1, 2, 1, 1), listOf(issuer.exchanges, issuer.prompts, issuer.signatures, issuer.deliveries))
        reopened.close()
    }
}
