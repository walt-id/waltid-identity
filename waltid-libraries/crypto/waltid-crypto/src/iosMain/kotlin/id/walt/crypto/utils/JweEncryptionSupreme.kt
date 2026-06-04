package id.walt.crypto.utils

import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.ECCurve
import at.asitplus.signum.indispensable.KeyAgreementPrivateValue
import at.asitplus.signum.indispensable.josef.JweAlgorithm
import at.asitplus.signum.indispensable.josef.JweEncrypted
import at.asitplus.signum.indispensable.josef.JweEncryption
import at.asitplus.signum.indispensable.josef.JweHeader
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.signum.indispensable.josef.toJsonWebKey
import at.asitplus.signum.indispensable.symmetric.AuthCapability
import at.asitplus.signum.indispensable.symmetric.NonceTrait
import at.asitplus.signum.indispensable.symmetric.SymmetricEncryptionAlgorithm
import at.asitplus.signum.indispensable.symmetric.SymmetricKey
import at.asitplus.signum.indispensable.symmetric.authTag
import at.asitplus.signum.indispensable.symmetric.hasDedicatedMac
import at.asitplus.signum.indispensable.symmetric.isAuthenticated
import at.asitplus.signum.indispensable.symmetric.keyFrom
import at.asitplus.signum.indispensable.symmetric.nonce
import at.asitplus.signum.indispensable.symmetric.requiresNonce
import at.asitplus.signum.supreme.agree.Ephemeral
import at.asitplus.signum.supreme.agree.keyAgreement
import at.asitplus.signum.supreme.symmetric.encrypt
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object JweEncryptionSupreme {

    suspend fun encryptEcdhEs(
        plaintext: ByteArray,
        recipientPublicKey: CryptoPublicKey.EC,
        encAlg: String,
        keyId: String? = null
    ): String {
        val jweEncryption = when (encAlg) {
            "A128GCM" -> JweEncryption.A128GCM
            "A256GCM" -> JweEncryption.A256GCM
            else -> error("Unsupported enc algorithm: $encAlg")
        }

        // 1. Generate ephemeral ECDH key pair
        val ephemeralKeyPair = KeyAgreementPrivateValue.ECDH.Ephemeral(ECCurve.SECP_256_R_1).getOrThrow()

        // 2. ECDH key agreement → shared secret z
        val z = ephemeralKeyPair.keyAgreement(recipientPublicKey).getOrThrow()

        // 3. Concat KDF → Content Encryption Key (CEK)
        val keyLenBits = jweEncryption.combinedEncryptionKeyLength.bits.toInt()
        val cekBytes = concatKdf(z = z, keyLenBits = keyLenBits, algId = jweEncryption.identifier)

        // 4. Create SymmetricKey from derived bytes
        val algorithm = jweEncryption.algorithm
        require(algorithm.requiresNonce())
        require(algorithm.isAuthenticated())
        val symmetricKey = keyFromIntermediate(algorithm, cekBytes)

        // 5. Build JWE header with ephemeral public key (epk)
        val ephemeralJwk = ephemeralKeyPair.publicValue.asCryptoPublicKey().toJsonWebKey()
        val jweHeader = JweHeader(
            algorithm = JweAlgorithm.ECDH_ES,
            encryption = jweEncryption,
            ephemeralKeyPair = ephemeralJwk,
            keyId = keyId,
        )

        // 6. Serialize header for AAD
        val headerSerialized = joseCompliantSerializer.encodeToString(jweHeader)
        val headerBytes = headerSerialized.encodeToByteArray()
        val aad = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(headerBytes).encodeToByteArray()

        // 7. AES-GCM encrypt
        val sealedBox = symmetricKey.encrypt(plaintext, aad).getOrThrow()

        // 8. Construct JWE compact serialization
        return JweEncrypted(
            header = jweHeader,
            headerAsParsed = headerBytes,
            encryptedKey = null,
            iv = sealedBox.nonce,
            ciphertext = sealedBox.encryptedData,
            authTag = sealedBox.authTag
        ).serialize()
    }

    fun concatKdfPublic(z: ByteArray, keyLenBits: Int, algId: String): ByteArray = concatKdf(z, keyLenBits, algId)

    private fun concatKdf(z: ByteArray, keyLenBits: Int, algId: String): ByteArray {
        val algIdBytes = algId.encodeToByteArray()
        val otherInfo = intTo4Bytes(algIdBytes.size) + algIdBytes +
            intTo4Bytes(0) + intTo4Bytes(0) + intTo4Bytes(keyLenBits)
        val hashInput = intTo4Bytes(1) + z + otherInfo
        val hash = SHA256().apply { update(hashInput) }.digest()
        return hash.sliceArray(0 until keyLenBits / 8)
    }

    private fun intTo4Bytes(value: Int): ByteArray = byteArrayOf(
        (value shr 24).toByte(), (value shr 16).toByte(),
        (value shr 8).toByte(), value.toByte()
    )
}

@Suppress("UNCHECKED_CAST")
internal fun keyFromIntermediate(
    algorithm: SymmetricEncryptionAlgorithm<*, *, *>,
    jweKeyBytes: ByteArray,
): SymmetricKey<AuthCapability.Authenticated<*>, NonceTrait.Required, *> {
    val typed = algorithm as SymmetricEncryptionAlgorithm<AuthCapability.Authenticated<*>, NonceTrait.Required, *>
    return (if (typed.hasDedicatedMac())
        typed.keyFrom(
            jweKeyBytes.drop(jweKeyBytes.size / 2).toByteArray(),
            jweKeyBytes.take(jweKeyBytes.size / 2).toByteArray()
        ).getOrThrow()
    else
        typed.keyFrom(jweKeyBytes).getOrThrow()
    ) as SymmetricKey<AuthCapability.Authenticated<*>, NonceTrait.Required, *>
}
