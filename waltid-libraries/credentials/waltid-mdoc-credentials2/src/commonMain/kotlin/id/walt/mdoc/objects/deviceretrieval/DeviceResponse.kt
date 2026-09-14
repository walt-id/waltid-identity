package id.walt.mdoc.objects.deviceretrieval

import id.walt.mdoc.objects.document.Document
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the top-level response from an mdoc to an mdoc reader.
 *
 * @see ISO/IEC 18013-5:2021, 8.3.2.1.2.3
 *
 * @property version The version of the DeviceResponse structure.
 * @property documents An optional list of returned documents. This is absent if the status is not OK.
 * @property documentErrors An optional map of document types to error codes for documents that were not returned.
 * @property status The overall status code of the response (0 indicates success).
 */
@Serializable
data class DeviceResponse(
    @SerialName("version")
    val version: String,

    @SerialName("documents")
    val documents: Array<Document>? = null,

    @SerialName("documentErrors")
    val documentErrors: Array<Pair<String, UInt>>? = null,

    @SerialName("status")
    val status: UInt
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as DeviceResponse

        if (version != other.version) return false
        if (documents != null) {
            if (other.documents == null) return false
            if (!documents.contentEquals(other.documents)) return false
        } else if (other.documents != null) return false
        if (documentErrors != null) {
            if (other.documentErrors == null) return false
            if (!documentErrors.contentEquals(other.documentErrors)) return false
        } else if (other.documentErrors != null) return false
        return status == other.status
    }

    override fun hashCode(): Int {
        var result = version.hashCode()
        result = 31 * result + (documents?.contentHashCode() ?: 0)
        result = 31 * result + (documentErrors?.contentHashCode() ?: 0)
        result = 31 * result + status.hashCode()
        return result
    }
}
