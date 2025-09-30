package id.walt.mdoc.objects.deviceretrieval

import id.walt.cose.CoseSign1
import id.walt.mdoc.encoding.ByteStringWrapper
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ValueTags

/**
 * Represents a request for a single document, specifying the data elements needed.
 *
 * @see ISO/IEC 18013-5:2021, 8.3.2.1.2.1
 *
 * @property itemsRequest A CBOR-tagged bytestring containing the detailed request for items within a document.
 * @property readerAuth Optional reader authentication signature specific to this document request.
 */
@OptIn(ExperimentalUnsignedTypes::class, ExperimentalSerializationApi::class)
@Serializable
data class DocRequest(
    @SerialName("itemsRequest")
    @ValueTags(24U) // Corresponds to CBOR tag #6.24 for an embedded CBOR data item
    val itemsRequest: ByteStringWrapper<ItemsRequest>,

    @SerialName("readerAuth")
    val readerAuth: CoseSign1? = null,
)
