@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.objects.deviceretrieval

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

/**
 * The decoded content of an `itemsRequest`, specifying the document type and the
 * desired namespaces and data elements.
 *
 * @see ISO/IEC 18013-5:2021, 8.3.2.1.2.1
 *
 * @property docType The document type being requested (e.g., "org.iso.18013.5.1.mDL").
 * @property namespaces A map where the key is the namespace identifier (e.g., "org.iso.18013.5.1")
 * and the value is a map of data element identifiers to a boolean `IntentToRetain` flag.
 * @property requestInfo Optional map for additional request information.
 */
@Serializable
data class ItemsRequest(
    @SerialName("docType")
    val docType: String,
    // This is a direct representation of the CDDL: NameSpace => (DataElementIdentifier => IntentToRetain)

    @SerialName("nameSpaces")
    val namespaces: Map<String, ItemsRequestList>,

    @SerialName("requestInfo")
    val requestInfo: Map<String, String>? = null
)

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

    /**
     * Serializes [ItemsRequestList.entries] as an "inline map",
     * having [ItemRequest.key] as the map key and [ItemRequest.value] as the map value,
     * for the map represented by [ItemsRequestList].
     */
    object ItemsRequestListSerializer : KSerializer<ItemsRequestList> {

        override val descriptor: SerialDescriptor = mapSerialDescriptor(
            keyDescriptor = PrimitiveSerialDescriptor("key", PrimitiveKind.INT),
            valueDescriptor = listSerialDescriptor<Byte>(),
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
