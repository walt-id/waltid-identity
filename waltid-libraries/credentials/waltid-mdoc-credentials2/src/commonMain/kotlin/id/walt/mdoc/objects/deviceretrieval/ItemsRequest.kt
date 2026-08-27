@file:OptIn(ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)

package id.walt.mdoc.objects.deviceretrieval

import id.walt.mdoc.encoding.decodeTextMap
import id.walt.mdoc.encoding.encodeTextMap
import id.walt.mdoc.encoding.extensionsExcluding
import id.walt.mdoc.encoding.fromCborElement
import id.walt.mdoc.encoding.requireNoExtensionCollisions
import id.walt.mdoc.encoding.toCborElement
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

/**
 * The decoded content of an `itemsRequest`, specifying the document type and the
 * desired namespaces and data elements.
 *
 * @see ISO/IEC 18013-5, ItemsRequest CDDL
 *
 * @property docType The document type being requested (e.g., "org.iso.18013.5.1.mDL").
 * @property namespaces A map where the key is the namespace identifier (e.g., "org.iso.18013.5.1")
 * and the value is a map of data element identifiers to a boolean `IntentToRetain` flag.
 * @property requestInfo Optional edition-2 constraints for this document request.
 * @property extensions Unrecognized ItemsRequest fields retained for wire round trips.
 */
@Serializable(with = ItemsRequestSerializer::class)
data class ItemsRequest(
    @SerialName("docType")
    val docType: String,
    // This is a direct representation of the CDDL: NameSpace => (DataElementIdentifier => IntentToRetain)

    @SerialName("nameSpaces")
    val namespaces: Map<String, ItemsRequestList>,

    @SerialName("requestInfo")
    val requestInfo: DocRequestInfo? = null,
    val extensions: Map<String, CborElement> = emptyMap(),
) {
    init {
        require(docType.isNotBlank()) { "ItemsRequest docType must not be blank" }
        require(namespaces.isNotEmpty()) { "ItemsRequest nameSpaces must not be empty" }
        require(namespaces.values.all { it.entries.isNotEmpty() }) { "Requested namespaces must not be empty" }
        require(requestInfo?.alternativeDataElements.orEmpty().all { alternative ->
            namespaces[alternative.requestedElement.namespace]?.entries?.any {
                it.key == alternative.requestedElement.elementIdentifier
            } == true
        }) { "Every alternativeDataElements requestedElement must identify a requested data element" }
        requireNoExtensionCollisions(extensions, ITEMS_REQUEST_FIELDS, "ItemsRequest")
    }
}
object ItemsRequestSerializer : KSerializer<ItemsRequest> {
    override val descriptor: SerialDescriptor = CborElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: ItemsRequest) {
        val fields = linkedMapOf<String, CborElement>()
        fields["docType"] = CborString(value.docType)
        fields["nameSpaces"] = value.namespaces.toCborElement(
            kotlinx.serialization.builtins.MapSerializer(
                String.serializer(),
                ItemsRequestList.serializer(),
            )
        )
        value.requestInfo?.let { fields["requestInfo"] = it.toCborElement(DocRequestInfo.serializer()) }
        fields.putAll(value.extensions)
        encoder.encodeTextMap(fields)
    }

    override fun deserialize(decoder: Decoder): ItemsRequest {
        val fields = decoder.decodeTextMap("ItemsRequest")
        return ItemsRequest(
            docType = (fields["docType"] as? CborString)?.value
                ?: throw SerializationException("ItemsRequest docType is required and must be text"),
            namespaces = fields["nameSpaces"]?.fromCborElement(
                kotlinx.serialization.builtins.MapSerializer(
                    String.serializer(),
                    ItemsRequestList.serializer(),
                )
            ) ?: throw SerializationException("ItemsRequest nameSpaces is required"),
            requestInfo = fields["requestInfo"]?.fromCborElement(DocRequestInfo.serializer()),
            extensions = fields.extensionsExcluding(ITEMS_REQUEST_FIELDS),
        )
    }
}

private val ITEMS_REQUEST_FIELDS = setOf("docType", "nameSpaces", "requestInfo")

data class ItemRequest(
    val key: String,
    val value: Boolean,
)

/**
 * Serializable list of ItemRequests
 */
@Serializable(with = ItemsRequestList.ItemsRequestListSerializer::class)
data class ItemsRequestList(
    val entries: List<ItemRequest>
) {
    init {
        require(entries.isNotEmpty()) { "A requested namespace must contain at least one data element" }
        require(entries.all { it.key.isNotBlank() }) { "Requested data element identifiers must not be blank" }
        require(entries.map { it.key }.distinct().size == entries.size) {
            "A requested namespace cannot repeat a data element identifier"
        }
    }

    /**
     * Serializes [ItemsRequestList.entries] as an "inline map",
     * having [ItemRequest.key] as the map key and [ItemRequest.value] as the map value,
     * for the map represented by [ItemsRequestList].
     */
    object ItemsRequestListSerializer : KSerializer<ItemsRequestList> {

        override val descriptor: SerialDescriptor = mapSerialDescriptor(
            keyDescriptor = PrimitiveSerialDescriptor("key", PrimitiveKind.STRING),
            valueDescriptor = PrimitiveSerialDescriptor("intentToRetain", PrimitiveKind.BOOLEAN),
        )

        override fun serialize(encoder: Encoder, value: ItemsRequestList) {
            encoder.encodeStructure(descriptor) {
                var index = 0
                value.entries.forEach {
                    this.encodeStringElement(descriptor, index++, it.key)
                    this.encodeBooleanElement(descriptor, index++, it.value)
                }
            }
        }

        override fun deserialize(decoder: Decoder): ItemsRequestList {
            val entries = mutableListOf<ItemRequest>()
            decoder.decodeStructure(descriptor) {
                lateinit var key: String
                var value: Boolean
                while (true) {
                    val index = decodeElementIndex(descriptor)
                    if (index == CompositeDecoder.Companion.DECODE_DONE) {
                        break
                    } else if (index % 2 == 0) {
                        key = decodeStringElement(descriptor, index)
                    } else if (index % 2 == 1) {
                        value = decodeBooleanElement(descriptor, index)
                        entries.plusAssign(ItemRequest(key, value))
                    }
                }
            }
            return ItemsRequestList(entries)
        }
    }
}
