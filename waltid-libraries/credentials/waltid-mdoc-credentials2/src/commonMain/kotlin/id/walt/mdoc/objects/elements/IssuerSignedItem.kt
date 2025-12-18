@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.objects.elements

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.cbor.ByteString

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
data class IssuerSignedItem(
    @SerialName(PROP_DIGEST_ID)
    val digestId: UInt,

    @SerialName(PROP_RANDOM)
    @ByteString
    val random: ByteArray,

    @SerialName(PROP_ELEMENT_ID)
    val elementIdentifier: String,

    @SerialName(PROP_ELEMENT_VALUE)
    val elementValue: Any
) {

    /**
     * Note: A custom `equals` implementation is required because the `elementValue` property is of type `Any`
     * and may contain arrays. The default `equals` for a `data class` would use reference equality
     * for arrays, leading to incorrect comparisons. This implementation correctly uses content-based
     * equality for all array types.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IssuerSignedItem) return false

        if (digestId != other.digestId) return false
        if (!random.contentEquals(other.random)) return false
        if (elementIdentifier != other.elementIdentifier) return false

        val otherValue = other.elementValue
        return when (elementValue) {
            is ByteArray -> otherValue is ByteArray && elementValue.contentEquals(otherValue)
            is IntArray -> otherValue is IntArray && elementValue.contentEquals(otherValue)
            is BooleanArray -> otherValue is BooleanArray && elementValue.contentEquals(otherValue)
            is CharArray -> otherValue is CharArray && elementValue.contentEquals(otherValue)
            is ShortArray -> otherValue is ShortArray && elementValue.contentEquals(otherValue)
            is LongArray -> otherValue is LongArray && elementValue.contentEquals(otherValue)
            is FloatArray -> otherValue is FloatArray && elementValue.contentEquals(otherValue)
            is DoubleArray -> otherValue is DoubleArray && elementValue.contentEquals(otherValue)
            is Array<*> -> otherValue is Array<*> && elementValue.contentDeepEquals(otherValue)
            else -> elementValue == otherValue
        }
    }

    /**
     * Note: A custom `hashCode` implementation is required to match the custom `equals` logic.
     * The default `hashCode` would be incorrect for arrays. This implementation correctly uses
     * content-based hashing for all array types to ensure a consistent contract with `equals`.
     */
    override fun hashCode(): Int {
        var result = digestId.hashCode()
        result = 31 * result + random.contentHashCode()
        result = 31 * result + elementIdentifier.hashCode()
        val valueHash = when (elementValue) {
            is ByteArray -> elementValue.contentHashCode()
            is IntArray -> elementValue.contentHashCode()
            is BooleanArray -> elementValue.contentHashCode()
            is CharArray -> elementValue.contentHashCode()
            is ShortArray -> elementValue.contentHashCode()
            is LongArray -> elementValue.contentHashCode()
            is FloatArray -> elementValue.contentHashCode()
            is DoubleArray -> elementValue.contentHashCode()
            is Array<*> -> elementValue.contentDeepHashCode()
            else -> elementValue.hashCode()
        }
        result = 31 * result + valueHash
        return result
    }

    companion object {
        internal const val PROP_DIGEST_ID = "digestID"
        internal const val PROP_RANDOM = "random"
        internal const val PROP_ELEMENT_ID = "elementIdentifier"
        internal const val PROP_ELEMENT_VALUE = "elementValue"
    }
}
