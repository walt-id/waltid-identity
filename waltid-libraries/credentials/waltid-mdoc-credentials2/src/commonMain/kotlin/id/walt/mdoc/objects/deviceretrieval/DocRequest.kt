@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)

package id.walt.mdoc.objects.deviceretrieval

import id.walt.cose.CoseSign1
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.encoding.decodeTextMap
import id.walt.mdoc.encoding.encodeTextMap
import id.walt.mdoc.encoding.extensionsExcluding
import id.walt.mdoc.encoding.fromCborElement
import id.walt.mdoc.encoding.fromTaggedByteString
import id.walt.mdoc.encoding.requireNoExtensionCollisions
import id.walt.mdoc.encoding.toCborElement
import id.walt.mdoc.encoding.toTaggedByteString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.ValueTags
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Represents a request for a single document, specifying the data elements needed.
 *
 * @see ISO/IEC DIS 18013-5:2026, DeviceRequest and DocRequest structures in 10.2
 *
 * @property itemsRequest A CBOR-tagged bytestring containing the detailed request for items within a document.
 * @property readerAuth Optional reader authentication signature specific to this document request.
 */
@OptIn(ExperimentalUnsignedTypes::class, ExperimentalSerializationApi::class)
@Serializable(with = DocRequestSerializer::class)
data class DocRequest(
    @SerialName("itemsRequest")
    @ValueTags(24U) // Corresponds to CBOR tag #6.24 for an embedded CBOR data item
    val itemsRequest: ByteStringWrapper<ItemsRequest>,

    @SerialName("readerAuth")
    val readerAuth: CoseSign1? = null,
    val extensions: Map<String, CborElement> = emptyMap(),
) {
    init {
        requireNoExtensionCollisions(extensions, DOC_REQUEST_FIELDS, "DocRequest")
    }

    companion object {
        fun fromValues(docType: String, requestedElements: Map<String, List<String>>, intentToRetain: Boolean = false) = DocRequest(
            itemsRequest = ByteStringWrapper(
                value = ItemsRequest(
                    docType = docType,
                    namespaces = requestedElements
                        .filterValues { it.isNotEmpty() }
                        .mapValues { (_, elems) ->
                            ItemsRequestList(elems.distinct().map { ItemRequest(it, intentToRetain) })
                        }
                )
            )
        )
    }
}

object DocRequestSerializer : KSerializer<DocRequest> {
    override val descriptor: SerialDescriptor = CborElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: DocRequest) {
        val fields = linkedMapOf<String, CborElement>()
        fields["itemsRequest"] = value.itemsRequest.toTaggedByteString(ItemsRequest.serializer())
        value.readerAuth?.let { fields["readerAuth"] = it.toCborElement(CoseSign1.serializer()) }
        fields.putAll(value.extensions)
        encoder.encodeTextMap(fields)
    }

    override fun deserialize(decoder: Decoder): DocRequest {
        val fields = decoder.decodeTextMap("DocRequest")
        return DocRequest(
            itemsRequest = fields["itemsRequest"]?.fromTaggedByteString(
                ItemsRequest.serializer(),
                "ItemsRequestBytes",
            ) ?: throw SerializationException("DocRequest itemsRequest is required"),
            readerAuth = fields["readerAuth"]?.fromCborElement(CoseSign1.serializer()),
            extensions = fields.extensionsExcluding(DOC_REQUEST_FIELDS),
        )
    }
}

private val DOC_REQUEST_FIELDS = setOf("itemsRequest", "readerAuth")
