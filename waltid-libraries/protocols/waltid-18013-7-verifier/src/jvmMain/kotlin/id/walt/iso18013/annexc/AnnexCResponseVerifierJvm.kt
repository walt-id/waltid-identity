@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.iso18013.annexc

import id.walt.cose.Cose
import id.walt.cose.CoseKey
import id.walt.cose.coseCompliantCbor
import id.walt.iso18013.annexc.cbor.Base64UrlNoPad
import id.walt.mdoc.objects.dcapi.DCAPIEncryptionInfo
import kotlinx.serialization.decodeFromByteArray
import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.crypto.hpke.HPKE
import java.math.BigInteger

object AnnexCResponseVerifierJvm : AnnexCResponseVerifier {

    private val hpke = HPKE(
        HPKE.mode_base,
        HPKE.kem_P256_SHA256,
        HPKE.kdf_HKDF_SHA256,
        HPKE.aead_AES_GCM128
    )

    private val p256 = requireNotNull(CustomNamedCurves.getByName("secp256r1")) { "Missing curve params for secp256r1" }

    override fun decryptToDeviceResponse(
        encryptedResponseB64: String,
        encryptionInfoB64: String,
        origin: String,
        recipientPrivateKey: ByteArray,
    ): ByteArray {
        val encryptedResponseCbor = Base64UrlNoPad.decode(encryptedResponseB64)
        val encryptedResponse =
            coseCompliantCbor.decodeFromByteArray(AnnexCEncryptedResponse.serializer(), encryptedResponseCbor)

        val encryptionInfoCbor = Base64UrlNoPad.decode(encryptionInfoB64)
        val encryptionInfo = coseCompliantCbor.decodeFromByteArray(DCAPIEncryptionInfo.serializer(), encryptionInfoCbor)

        val hpkeInfo = AnnexCTranscriptBuilder.computeHpkeInfo(encryptionInfoB64, origin)

        val recipientKeyPair = buildRecipientKeyPair(
            recipientPrivateKey = recipientPrivateKey,
            recipientPublicKey = encryptionInfo.encryptionParameters.recipientPublicKey
        )

        return try {
            hpke.open(
                encryptedResponse.response.enc,
                recipientKeyPair,
                hpkeInfo,
                byteArrayOf(),
                encryptedResponse.response.cipherText,
                null,
                null,
                null
            )
        } catch (e: InvalidCipherTextException) {
            throw IllegalArgumentException("HPKE decryption failed (wrong key, origin/encryptionInfo mismatch, or corrupted response)", e)
        }
    }

    private fun buildRecipientKeyPair(
        recipientPrivateKey: ByteArray,
        recipientPublicKey: CoseKey
    ) = recipientPrivateKey.normalizeP256Scalar().let { normalized ->
        val expectedPublicKeyBytes = recipientPublicKey.toUncompressedP256Point()
        hpke.deserializePrivateKey(normalized, expectedPublicKeyBytes).also { keyPair ->
            val derivedPublicKeyBytes = p256.g.multiply(BigInteger(1, normalized))
                .normalize()
                .getEncoded(false)
            require(derivedPublicKeyBytes.contentEquals(expectedPublicKeyBytes)) {
                "recipientPrivateKey does not match encryptionInfo.recipientPublicKey"
            }
            require(expectedPublicKeyBytes.contentEquals(hpke.serializePublicKey(keyPair.public))) {
                "HPKE keypair construction mismatch (recipient public key encoding differs)"
            }
        }
    }

    private fun CoseKey.toUncompressedP256Point(): ByteArray {
        require(kty == Cose.KeyTypes.EC2) { "recipientPublicKey must be EC2" }
        require(crv == Cose.EllipticCurves.P_256) { "recipientPublicKey must be P-256" }
        val xBytes = requireNotNull(x) { "recipientPublicKey.x is missing" }
        val yBytes = requireNotNull(y) { "recipientPublicKey.y is missing" }
        require(xBytes.size == 32) { "recipientPublicKey.x must be 32 bytes for P-256" }
        require(yBytes.size == 32) { "recipientPublicKey.y must be 32 bytes for P-256" }
        return byteArrayOf(0x04) + xBytes + yBytes
    }

    private fun ByteArray.normalizeP256Scalar(): ByteArray {
        // Ensure unsigned (BigInteger may add a leading 0x00 when serializing).
        val bi = BigInteger(1, this)
        require(bi.signum() == 1) { "recipientPrivateKey must be non-zero" }
        require(bi < p256.n) { "recipientPrivateKey must be < curve order" }
        val bytes = bi.toByteArray()
        return when {
            bytes.size == 32 -> bytes
            bytes.size == 33 && bytes[0] == 0.toByte() -> bytes.copyOfRange(1, 33)
            bytes.size < 32 -> ByteArray(32 - bytes.size) + bytes
            else -> throw IllegalArgumentException("recipientPrivateKey must be a 32-byte P-256 scalar")
        }
    }
}
