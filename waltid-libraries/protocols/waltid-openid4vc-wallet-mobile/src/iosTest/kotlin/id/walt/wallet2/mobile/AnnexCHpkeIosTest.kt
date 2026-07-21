package id.walt.wallet2.mobile

import at.asitplus.signum.supreme.asymmetric.HPKE
import id.walt.cose.Cose
import id.walt.cose.CoseKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AnnexCHpkeIosTest {
    @Test
    fun walletHpkeOutputUsesP256EncapsulationAndAesGcmTag() = runTest {
        val hpke = HPKE(
            kem = HPKE.KEM.DHKEM_P256_HKDF_SHA256,
            kdf = HPKE.KDF.HKDF_SHA256,
            aead = HPKE.AEAD.AES_128_GCM,
        )
        val recipient = hpke.kem.DeriveKeyPair(ByteArray(32) { (it + 1).toByte() })
        val publicKey = hpke.kem.SerializePublicKey(recipient.pk)
        val plaintext = ByteArray(128) { (it + 1).toByte() }
        val info = ByteArray(64) { (it * 3).toByte() }

        val encrypted = encryptAnnexCHpke(
            recipientPublicKey = CoseKey(
                kty = Cose.KeyTypes.EC2,
                crv = Cose.EllipticCurves.P_256,
                x = publicKey.copyOfRange(1, 33),
                y = publicKey.copyOfRange(33, 65),
            ),
            plaintext = plaintext,
            info = info,
        )
        assertEquals(65, encrypted.enc.size)
        assertEquals(plaintext.size + 16, encrypted.cipherText.size)
        assertFalse(plaintext.contentEquals(encrypted.cipherText.copyOfRange(0, plaintext.size)))
        assertContentEquals(
            plaintext,
            hpke.OpenBase(
                enc = encrypted.enc,
                skR = recipient.sk,
                info = info,
                aad = byteArrayOf(),
                ct = encrypted.cipherText,
            ),
        )
    }
}
