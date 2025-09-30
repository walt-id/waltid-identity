package id.walt.mdoc.objects.serverretrieval

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the `ItemsRequest` structure, which details a request for a single document
 * within a `ServerRequest`. This is sent by an mdoc reader to an Issuing Authority's server endpoint.
 *
 * @see ISO/IEC 18013-5:xxxx(E), 8.3.2.2.2.1 (Server retrieval mdoc request)
 *
 * @property docType The document type identifier for the credential being requested (e.g., "org.iso.18013.5.1.mDL").
 * @property namespaces A map where the key is a namespace identifier (e.g., "org.iso.18013.5.1") and the value
 * is another map. The inner map's key is the `DataElementIdentifier` and its boolean value is the
 * `IntentToRetain` flag.
 * @property requestInfo An optional map for proprietary or future extensions.
 * NOTE: The spec allows values of `any` type, but this implementation uses `String` for simplicity.
 */
@Serializable
data class ServerItemsRequest(
    @SerialName("docType")
    val docType: String,

    @SerialName("nameSpaces")
    val namespaces: Map<String, Map<String, Boolean>>,

    @SerialName("requestInfo")
    val requestInfo: Map<String, String>? = null
)
