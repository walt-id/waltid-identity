package id.walt.verifier.openid.transactiondata

import kotlin.test.Test
import kotlin.test.assertEquals

class TransactionDataSelectionTest {
    @Test
    fun `keeps only matching credential id items`() {
        val paymentCredential = TransactionDataTestFixtures.transactionData(
            credentialIds = listOf("payment_credential"),
            amount = "42.00",
            payee = "ACME Corp",
        )
        val loyaltyCredential = TransactionDataTestFixtures.transactionData(
            credentialIds = listOf("loyalty_credential"),
            amount = "5.00",
            payee = "Coffee Shop",
        )

        val filtered = filterTransactionDataForCredentialId(
            transactionData = listOf(paymentCredential, loyaltyCredential),
            credentialId = "payment_credential",
        )

        assertEquals(listOf(paymentCredential), filtered)
    }
}
