package id.walt.itb

import id.walt.wallet2.handlers.WalletScaPresentationAuthorizer
import id.waltid.openid4vp.wallet.presentation.ScaAuthenticationMethods
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class PaymentProfileTest {
    @Test
    fun `TS12 nested payment is bound to the verified SD-JWT holder proof`() = runTest {
        val payment = WalletFixtures.payment()
        // Synthetic factor evidence exercises the proof contract, not real user authentication.
        val proof = WalletFixtures().presentPayment(WalletScaPresentationAuthorizer { _, _ ->
            ScaAuthenticationMethods.PossessionAndInherence(
                ScaAuthenticationMethods.Possession.OTHER, ScaAuthenticationMethods.Inherence.OTHER,
            )
        })
        val expectedHash = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(payment.toByteArray(Charsets.US_ASCII))
        )
        assertEquals(listOf(expectedHash), proof.getValue("transaction_data_hashes").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("sha-256", proof["transaction_data_hashes_alg"]?.jsonPrimitive?.content)
        assertEquals("origin:${WalletFixtures.VERIFIER}", proof["aud"]?.jsonPrimitive?.content)
        assertEquals("itb-presentation-nonce", proof["nonce"]?.jsonPrimitive?.content)
        assertEquals("dc_api", proof["response_mode"]?.jsonPrimitive?.content)
        assertFalse(proof["jti"]?.jsonPrimitive?.content.isNullOrBlank())
        assertEquals(Json.parseToJsonElement("""[{"possession":"other"},{"inherence":"other"}]"""), proof["amr"])
    }

    @Test
    fun `TS12 payment cannot invent authentication evidence`() = runTest {
        assertFailsWith<IllegalArgumentException> { WalletFixtures().presentPayment(null) }
    }
}
