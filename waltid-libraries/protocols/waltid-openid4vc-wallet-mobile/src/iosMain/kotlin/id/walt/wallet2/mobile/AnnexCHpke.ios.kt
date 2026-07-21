package id.walt.wallet2.mobile

import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.ECCurve
import at.asitplus.signum.supreme.asymmetric.HPKE
import id.walt.cose.CoseKey

internal actual suspend fun encryptAnnexCHpke(
    recipientPublicKey: CoseKey,
    plaintext: ByteArray,
    info: ByteArray,
): AnnexCHpkeCiphertext {
    val publicKey = CryptoPublicKey.EC.fromUncompressed(
        curve = ECCurve.SECP_256_R_1,
        x = requireNotNull(recipientPublicKey.x),
        y = requireNotNull(recipientPublicKey.y),
    )
    val hpke = HPKE(
        kem = HPKE.KEM.DHKEM_P256_HKDF_SHA256,
        kdf = HPKE.KDF.HKDF_SHA256,
        aead = HPKE.AEAD.AES_128_GCM,
    )
    val sealed = hpke.SealBase(publicKey, info, byteArrayOf(), plaintext)
    return AnnexCHpkeCiphertext(sealed.encapsulatedSecret, sealed.ciphertext)
}
