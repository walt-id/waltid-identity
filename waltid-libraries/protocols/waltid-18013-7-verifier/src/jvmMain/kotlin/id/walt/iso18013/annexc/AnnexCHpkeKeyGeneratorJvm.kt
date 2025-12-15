package id.walt.iso18013.annexc

import id.walt.cose.Cose
import id.walt.cose.CoseKey
import org.bouncycastle.crypto.hpke.HPKE

data class AnnexCHpkeKeyPair(
    val recipientPrivateKey: ByteArray,
    val recipientPublicKey: CoseKey,
)

object AnnexCHpkeKeyGeneratorJvm {

    private val hpke = HPKE(
        HPKE.mode_base,
        HPKE.kem_P256_SHA256,
        HPKE.kdf_HKDF_SHA256,
        HPKE.aead_AES_GCM128
    )

    fun generateRecipientKeyPair(): AnnexCHpkeKeyPair {
        val kp = hpke.generatePrivateKey()
        val publicKeyBytes = hpke.serializePublicKey(kp.public) // uncompressed SEC1 point
        val privateKeyBytes = hpke.serializePrivateKey(kp.private) // 32-byte scalar

        require(publicKeyBytes.size == 65 && publicKeyBytes[0] == 0x04.toByte()) {
            "Unexpected P-256 public key encoding"
        }
        require(privateKeyBytes.size == 32) { "Unexpected P-256 private key size: ${privateKeyBytes.size}" }

        val cosePublicKey = CoseKey(
            kty = Cose.KeyTypes.EC2,
            crv = Cose.EllipticCurves.P_256,
            x = publicKeyBytes.copyOfRange(1, 33),
            y = publicKeyBytes.copyOfRange(33, 65),
        )

        return AnnexCHpkeKeyPair(
            recipientPrivateKey = privateKeyBytes,
            recipientPublicKey = cosePublicKey,
        )
    }
}

