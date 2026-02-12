@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.objects.deviceretrieval

import id.walt.cose.CoseSign1
import id.walt.cose.coseCompliantCbor
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.mdoc.encoding.ByteStringWrapper
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToByteArray

/**
 * Represents the top-level request from a mdoc reader to a mdoc.
 * It encapsulates one or more specific document requests.
 *
 * @see ISO/IEC 18013-5:2021, 8.3.2.1.2.1
 *
 * @property version The version of the DeviceRequest structure.
 * @property docRequests A list of one or more document requests.
 * @property readerAuthAll Optional structure for mdoc reader authentication for all documents in the request.
 * @property deviceRequestInfo Optional additional information about the overall request.
 */
@Serializable
data class DeviceRequest(
    @SerialName("version")
    val version: String,
    @SerialName("docRequests")
    val docRequests: List<DocRequest>,

    @SerialName("readerAuthAll")
    val readerAuthAll: List<CoseSign1>? = null,

    //@SerialName("deviceRequestInfo")
    //val deviceRequestInfo: ByteStringWrapper<DeviceRequestInfo>? = null
) {
    companion object {
        const val VERSION = "1.0"
    }

    fun encodeToBase64Url(): String = coseCompliantCbor.encodeToByteArray(this).encodeToBase64Url()

    constructor(docType: String, requestedElements: Map<String, List<String>>, intentToRetain: Boolean = false) : this(
        version = VERSION,
        docRequests = listOf(
            DocRequest(
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
        )
    )

}

