@file:OptIn(ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)

package id.walt.mdoc.objects.elements

import id.walt.mdoc.encoding.decodeTextMap
import id.walt.mdoc.encoding.encodeTextMap
import id.walt.mdoc.encoding.extensionsExcluding
import id.walt.mdoc.encoding.fromCborElement
import id.walt.mdoc.encoding.requireNoExtensionCollisions
import id.walt.mdoc.encoding.toCborElement
import kotlinx.serialization.KSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.CborByteString
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
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
@Serializable(with = IssuerSignedItemSerializer::class)
data class IssuerSignedItem(
    @SerialName(PROP_DIGEST_ID)
    val digestId: UInt,

    @SerialName(PROP_RANDOM)
    val random: ByteArray,

    @SerialName(PROP_ELEMENT_ID)
    val elementIdentifier: String,

    @SerialName(PROP_ELEMENT_VALUE)
    val elementValue: CborElement,
    val extensions: Map<String, CborElement> = emptyMap(),
) {

    init {
        requireNoExtensionCollisions(extensions, ISSUER_SIGNED_ITEM_FIELDS, "IssuerSignedItem")
    }

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
        if (extensions != other.extensions) return false

        return true
    }

    override fun hashCode(): Int {
        var result = digestId.hashCode()
        result = 31 * result + random.contentHashCode()
        result = 31 * result + elementIdentifier.hashCode()
        result = 31 * result + elementValue.hashCode()
        result = 31 * result + extensions.hashCode()
        return result
    }
}

object IssuerSignedItemSerializer : KSerializer<IssuerSignedItem> {
    override val descriptor: SerialDescriptor = CborElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: IssuerSignedItem) {
        val fields = linkedMapOf<String, CborElement>(
            IssuerSignedItem.PROP_DIGEST_ID to value.digestId.toCborElement(UInt.serializer()),
            IssuerSignedItem.PROP_RANDOM to CborByteString(value.random),
            IssuerSignedItem.PROP_ELEMENT_ID to CborString(value.elementIdentifier),
            IssuerSignedItem.PROP_ELEMENT_VALUE to value.elementValue,
        )
        fields.putAll(value.extensions)
        encoder.encodeTextMap(fields)
    }

    override fun deserialize(decoder: Decoder): IssuerSignedItem {
        val fields = decoder.decodeTextMap("IssuerSignedItem")
        return IssuerSignedItem(
            digestId = fields[IssuerSignedItem.PROP_DIGEST_ID]?.fromCborElement(UInt.serializer())
                ?: throw SerializationException("IssuerSignedItem digestID is required"),
            random = (fields[IssuerSignedItem.PROP_RANDOM] as? CborByteString)?.toByteArray()
                ?: throw SerializationException("IssuerSignedItem random is required and must be a byte string"),
            elementIdentifier = (fields[IssuerSignedItem.PROP_ELEMENT_ID] as? CborString)?.value
                ?: throw SerializationException("IssuerSignedItem elementIdentifier is required and must be text"),
            elementValue = fields[IssuerSignedItem.PROP_ELEMENT_VALUE]
                ?: throw SerializationException("IssuerSignedItem elementValue is required"),
            extensions = fields.extensionsExcluding(ISSUER_SIGNED_ITEM_FIELDS),
        )
    }
}

private val ISSUER_SIGNED_ITEM_FIELDS = setOf(
    IssuerSignedItem.PROP_DIGEST_ID,
    IssuerSignedItem.PROP_RANDOM,
    IssuerSignedItem.PROP_ELEMENT_ID,
    IssuerSignedItem.PROP_ELEMENT_VALUE,
)
