package id.walt.mdoc.objects.deviceretrieval

import id.walt.cose.CoseSign1
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the top-level request from an mdoc reader to an mdoc.
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
)

