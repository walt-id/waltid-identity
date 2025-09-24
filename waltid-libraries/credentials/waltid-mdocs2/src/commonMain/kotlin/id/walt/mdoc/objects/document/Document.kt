package id.walt.mdoc.objects.document

import id.walt.mdoc.objects.DeviceSigned
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a single document returned within a `DeviceResponse`. This is the main container
 * for the data elements of a specific credential, such as a Mobile Driving Licence (mDL).
 *
 * It separates data elements into those signed by the issuer and those signed by the mdoc holder's device.
 *
 * @see ISO/IEC 18013-5:xxxx(E), 8.3.2.1.2.3 (Device retrieval mdoc response)
 *
 * @property docType The document type identifier for the credential being presented (e.g., "org.iso.18013.5.1.mDL"). [cite: 5200]
 * @property issuerSigned A mandatory structure containing data elements signed by the issuing authority and the
 * Mobile Security Object (MSO) for their verification. [cite: 5202, 5203, 5204]
 * @property deviceSigned A mandatory structure containing data elements signed by the mdoc's device key and the
 * `DeviceAuth` structure for holder authentication. It must be present even if no data elements are returned
 * within it, as `DeviceAuth` is essential for session integrity. [cite: 5205, 5206, 5207]
 * @property errors An optional map that reports errors for any requested data elements that could not be returned.
 * The map structure is `Namespace -> (DataElementIdentifier -> ErrorCode)`. [cite: 5208]
 */
@Serializable
data class Document(
    @SerialName("docType")
    val docType: String,

    @SerialName("issuerSigned")
    val issuerSigned: IssuerSigned,

    @SerialName("deviceSigned")
    val deviceSigned: DeviceSigned? = null,

    @SerialName("errors")
    val errors: Map<String, Map<String, Int>>? = null
)
