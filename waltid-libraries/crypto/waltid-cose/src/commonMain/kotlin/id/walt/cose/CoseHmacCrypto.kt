@file:OptIn(CryptographyProviderApi::class)

package id.walt.cose

import dev.whyoleg.cryptography.CryptographyAlgorithmId
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.CryptographyProviderApi
import dev.whyoleg.cryptography.algorithms.Digest
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.algorithms.SHA384
import dev.whyoleg.cryptography.algorithms.SHA512

/** A suspendable interface for a COSE-compatible MAC creator. */
fun interface CoseMacCreator {
    suspend fun mac(data: ByteArray): ByteArray
}

/** A suspendable interface for a COSE-compatible MAC verifier. */
fun interface CoseMacVerifier {
    suspend fun verify(data: ByteArray, tag: ByteArray): Boolean
}

/**
 * Represents a symmetric key for use with COSE HMAC algorithms.
 *
 * @property keyBytes The raw byte array of the secret key.
 */
data class CoseHmacKey(val keyBytes: ByteArray) {

    fun toCoseMacCreator(
        algorithm: Int?,
        provider: CryptographyProvider = CryptographyProvider.Default,
    ): CoseMacCreator {
        return CoseMacCreator { dataToMac ->
            val fullTag = hmacKey(provider, algorithm).signatureGenerator().generateSignature(dataToMac)
            if (algorithm == Cose.Algorithm.HMAC_256_64) fullTag.copyOf(8) else fullTag
        }
    }

    fun toCoseMacVerifier(
        algorithm: Int?,
        provider: CryptographyProvider = CryptographyProvider.Default,
    ): CoseMacVerifier {
        return CoseMacVerifier { data, tag ->
            val key = hmacKey(provider, algorithm)
            if (algorithm == Cose.Algorithm.HMAC_256_64) {
                val expected = key.signatureGenerator().generateSignature(data).copyOf(8)
                constantTimeEquals(tag, expected)
            } else {
                key.signatureVerifier().tryVerifySignature(data, tag)
            }
        }
    }

    private suspend fun hmacKey(provider: CryptographyProvider, algorithm: Int?): HMAC.Key {
        val digest: CryptographyAlgorithmId<Digest> = when (algorithm) {
            Cose.Algorithm.HMAC_256_64, Cose.Algorithm.HMAC_256 -> SHA256
            Cose.Algorithm.HMAC_384 -> SHA384
            Cose.Algorithm.HMAC_512 -> SHA512
            else -> throw IllegalArgumentException("Unsupported or unknown HMAC algorithm: $algorithm")
        }
        return provider.get(HMAC).keyDecoder(digest).decodeFromByteArray(HMAC.Key.Format.RAW, keyBytes)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        other as CoseHmacKey
        return keyBytes.contentEquals(other.keyBytes)
    }

    override fun hashCode(): Int {
        return keyBytes.contentHashCode()
    }
}

private fun constantTimeEquals(first: ByteArray, second: ByteArray): Boolean {
    if (first.size != second.size) return false
    var difference = 0
    for (index in first.indices) {
        difference = difference or (first[index].toInt() xor second[index].toInt())
    }
    return difference == 0
}
