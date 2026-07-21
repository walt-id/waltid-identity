package id.walt.wallet2.mobile

import id.walt.cose.JWKKeyCoseTransform.getCosePublicKey
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals

class AnnexCHpkeAndroidTest {
    @Test
    fun walletHpkeOutputDecryptsWithTheMatchedRecipientKey() = runTest {
        val recipient = JWKKey.generate(KeyType.secp256r1)
        val plaintext = ByteArray(128) { (it + 1).toByte() }
        val info = ByteArray(64) { (it * 3).toByte() }

        val encrypted = encryptAnnexCHpke(
            recipientPublicKey = recipient.getCosePublicKey(),
            plaintext = plaintext,
            info = info,
        )
        val decrypted = recipient.decryptHpke(
            cipherTextWithEnc = encrypted.enc + encrypted.cipherText,
            info = info,
            aad = null,
        )

        assertContentEquals(plaintext, decrypted)
    }
}
