@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.objects.elements

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.CborElement
import org.kotlincrypto.random.CryptoRand

/**
 * Represents a single data element attested to by the issuing authority.
 *
 * This structure contains the actual data element (e.g., family name), a random value (salt),
 * and a digest ID that links it to a specific hash within the Mobile Security Object (MSO).
 * The combination of these elements allows a verifier to confirm that the data has not been
 * tampered with since it was issued.
 *
 * @see ISO/IEC 18013-5:xxxx(E), 8.3.2.1.2.3 (Device retrieval mdoc response)
 * @see ISO/IEC 18013-5:xxxx(E), 9.1.2.5 (Message digest function)
 *
 * @property digestId A unique unsigned integer that maps this item to a specific digest in the MSO's `valueDigests` map for its namespace.
 * @property random A unique, unpredictable random value (salt) of at least 16 bytes. This ensures the final digest does not leak information about the content of `elementValue`.
 * @property elementIdentifier The identifier for the data element (e.g., "family_name").
 * @property elementValue The actual value of the data element. Its type can be any valid CBOR type, represented here as `Any`.
 */
@Serializable
data class IssuerSignedItem(
    @SerialName(PROP_DIGEST_ID)
    val digestId: UInt,

    @SerialName(PROP_RANDOM)
    @ByteString
    val random: ByteArray,

    @SerialName(PROP_ELEMENT_ID)
    val elementIdentifier: String,

    @SerialName(PROP_ELEMENT_VALUE)
    val elementValue: CborElement
) {

    companion object {
        internal const val PROP_DIGEST_ID = "digestID"
        internal const val PROP_RANDOM = "random"
        internal const val PROP_ELEMENT_ID = "elementIdentifier"
        internal const val PROP_ELEMENT_VALUE = "elementValue"

        fun create(digestId: UInt, elementIdentifier: String, elementValue: CborElement): IssuerSignedItem {
            val randomSalt = CryptoRand.nextBytes(ByteArray(24)) // must be at least 16 bytes

            val issuerSignedItem = IssuerSignedItem(digestId, randomSalt, elementIdentifier, elementValue)

            return issuerSignedItem
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IssuerSignedItem) return false

        if (digestId != other.digestId) return false
        if (!random.contentEquals(other.random)) return false
        if (elementIdentifier != other.elementIdentifier) return false
        if (elementValue != other.elementValue) return false

        return true
    }

    override fun hashCode(): Int {
        var result = digestId.hashCode()
        result = 31 * result + random.contentHashCode()
        result = 31 * result + elementIdentifier.hashCode()
        result = 31 * result + elementValue.hashCode()
        return result
    }
}
