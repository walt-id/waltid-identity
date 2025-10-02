package id.walt.mdoc.objects.dcapi

import id.walt.cose.CoseKey
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString

/**
 * Represents the parameters needed for Hybrid Public Key Encryption (HPKE).
 * This structure is part of the overall `EncryptionInfo` and is encoded as a CBOR map.
 *
 * @see ISO/IEC TS 18013-7:2024(en), Annex C.2
 *
 * @property nonce A cryptographically random value with at least 16 bytes of entropy.
 * @property recipientPublicKey The public key of the mdoc reader (the recipient), encoded as a COSE_Key.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DCAPIEncryptionParameters(
    /** Nonce */
    @ByteString
    @SerialName("nonce")
    val nonce: ByteArray,

    /** The recipients public key */
    @SerialName("recipientPublicKey")
    val recipientPublicKey: CoseKey
) {
    init {
        // As per ISO/IEC TS 18013-7:2024(en), C.2, the nonce must have sufficient entropy.
        require(nonce.size >= 16) { "Nonce must be at least 16 bytes." }
    }

    /**
     * Note: `equals` and `hashCode` are manually overridden because the default implementation for a
     * `data class` uses reference equality for `ByteArray` properties. This override ensures
     * content-based equality.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as DCAPIEncryptionParameters

        if (!nonce.contentEquals(other.nonce)) return false
        if (recipientPublicKey != other.recipientPublicKey) return false

        return true
    }

    override fun hashCode(): Int {
        var result = nonce.contentHashCode()
        result = 31 * result + recipientPublicKey.hashCode()
        return result
    }

}
