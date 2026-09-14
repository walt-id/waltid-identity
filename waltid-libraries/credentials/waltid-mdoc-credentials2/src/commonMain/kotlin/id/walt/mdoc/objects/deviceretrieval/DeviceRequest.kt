@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.objects.deviceretrieval

import id.walt.cose.CoseSign1
import id.walt.cose.coseCompliantCbor
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.mdoc.encoding.ByteStringWrapper
import kotlinx.serialization.*
import kotlinx.serialization.cbor.ValueTags

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

    @SerialName("deviceRequestInfo")
    @ValueTags(24u)
    val deviceRequestInfo: ByteStringWrapper<DeviceRequestInfo>? = null
) {
    companion object {
        const val VERSION = "1.0"
        const val VERSION_WITH_SIGNING = "1.1"

        fun decodeFromBase64Url(base64Url: String): DeviceRequest {
            return coseCompliantCbor.decodeFromByteArray<DeviceRequest>(base64Url.decodeFromBase64Url())
        }

        fun convertMappingToDocRequests(docTypeRequestedElements: Map<String, Map<String, List<String>>>) =
            docTypeRequestedElements.map { (docType, requestedElements) ->
                DocRequest.fromValues(docType, requestedElements)
            }
    }

    fun print() {
        println("[DeviceRequest (version $version)]")
        docRequests.forEach { docRequest ->
            docRequest.itemsRequest.value.let { itemRequests ->
                println("> ItemRequest for \"${itemRequests.docType}\" - requestInfo=${itemRequests.requestInfo}")
                itemRequests.namespaces.forEach { namespace ->
                    println(">> Namespace ${namespace.key}")
                    namespace.value.entries.forEachIndexed { idx, itemRequest ->
                        println(">>> ${idx + 1}/${namespace.value.entries.size}: Item \"${itemRequest.key}\" - ${itemRequest.value}")
                    }
                }
            }
            println("Doc request ReaderAuth: ${docRequest.readerAuth?.toString()}")
        }
        println("Device request info: ${deviceRequestInfo?.value?.useCases?.joinToString { it.toString() }}")
        println("Device request ReaderAuthAll: $readerAuthAll")
    }

    fun encodeToBase64Url(): String = coseCompliantCbor.encodeToByteArray(this).encodeToBase64Url()

    /** Request multiple credentials
     * @param docTypeRequestedElements Doctype -> Namespace -> Element Identifier
     */
    constructor(docTypeRequestedElements: Map<String, Map<String, List<String>>>, intentToRetain: Boolean = false) : this(
        version = VERSION,
        docRequests = convertMappingToDocRequests(docTypeRequestedElements)
    )

    /** Request individual credential */
    constructor(docType: String, requestedElements: Map<String, List<String>>, intentToRetain: Boolean = false) : this(
        version = VERSION,
        docRequests = listOf(
            DocRequest.fromValues(docType, requestedElements, intentToRetain)
        )
    )

}
