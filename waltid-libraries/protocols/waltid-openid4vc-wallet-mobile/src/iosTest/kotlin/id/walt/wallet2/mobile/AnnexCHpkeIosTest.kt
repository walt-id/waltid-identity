package id.walt.wallet2.mobile

import id.walt.cose.JWKKeyCoseTransform.getCosePublicKey
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AnnexCHpkeIosTest {
    @Test
    fun walletHpkeOutputUsesP256EncapsulationAndAesGcmTag() = runTest {
        val recipient = JWKKey.importJWK(
            """{"kty":"EC","crv":"P-256","x":"y4ajD4aIXGiLGqiF81nN5HvBFvBEvrZcgFsp5VIJO30","y":"jyrZRfxKz113LQNg2x5f7Nu4fwW5Ov5gCzhPaTZuTCg"}""",
        ).getOrThrow()
        val plaintext = ByteArray(128) { (it + 1).toByte() }
        val info = ByteArray(64) { (it * 3).toByte() }

        val encrypted = encryptAnnexCHpke(
            recipientPublicKey = recipient.getCosePublicKey(),
            plaintext = plaintext,
            info = info,
        )
        assertEquals(65, encrypted.enc.size)
        assertEquals(plaintext.size + 16, encrypted.cipherText.size)
        assertFalse(plaintext.contentEquals(encrypted.cipherText.copyOfRange(0, plaintext.size)))
    }
}
