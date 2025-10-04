package id.walt.cose

import org.kotlincrypto.macs.hmac.sha2.HmacSHA256
import org.kotlincrypto.macs.hmac.sha2.HmacSHA384
import org.kotlincrypto.macs.hmac.sha2.HmacSHA512

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

    fun toCoseMacCreator(algorithm: Int?): CoseMacCreator {
        return CoseMacCreator { dataToMac ->
            when (algorithm) {
                Cose.Algorithm.HMAC_256_64 -> {
                    val fullTag = HmacSHA256(keyBytes).doFinal(dataToMac)
                    fullTag.copyOf(8)
                }
                Cose.Algorithm.HMAC_256 -> HmacSHA256(keyBytes).doFinal(dataToMac)
                Cose.Algorithm.HMAC_384 -> HmacSHA384(keyBytes).doFinal(dataToMac)
                Cose.Algorithm.HMAC_512 -> HmacSHA512(keyBytes).doFinal(dataToMac)
                else -> throw IllegalArgumentException("Unsupported or unknown HMAC algorithm: $algorithm")
            }
        }
    }

    fun toCoseMacVerifier(algorithm: Int?): CoseMacVerifier {
        return CoseMacVerifier { data, tag ->
            val expectedTag = when (algorithm) {
                Cose.Algorithm.HMAC_256_64 -> HmacSHA256(keyBytes).doFinal(data).copyOf(8)
                Cose.Algorithm.HMAC_256 -> HmacSHA256(keyBytes).doFinal(data)
                Cose.Algorithm.HMAC_384 -> HmacSHA384(keyBytes).doFinal(data)
                Cose.Algorithm.HMAC_512 -> HmacSHA512(keyBytes).doFinal(data)
                else -> throw IllegalArgumentException("Unsupported or unknown HMAC algorithm: $algorithm")
            }
            tag.contentEquals(expectedTag)
        }
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
