package id.walt.itb

import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class PaymentProfileTest {
    @Test
    fun `TS12 nested payment is bound to the verified SD-JWT holder proof`() = runTest {
        val payment = WalletFixtures.payment()
        // WAL-1295 covers this generic SD-JWT + nested-payment binding, not normative SCA.
        val proof = WalletFixtures().present(transactions = listOf(payment))
        val expectedHash = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(payment.toByteArray(Charsets.US_ASCII))
        )
        assertEquals(listOf(expectedHash), proof.getValue("transaction_data_hashes").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("sha-256", proof["transaction_data_hashes_alg"]?.jsonPrimitive?.content)
        assertEquals(WalletFixtures.CLIENT_ID, proof["aud"]?.jsonPrimitive?.content)
        assertEquals("itb-presentation-nonce", proof["nonce"]?.jsonPrimitive?.content)
    }
}
