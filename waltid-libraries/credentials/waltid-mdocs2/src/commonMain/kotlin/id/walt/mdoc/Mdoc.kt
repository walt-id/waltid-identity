@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc

import id.walt.mdoc.credsdata.DocumentType
import id.walt.mdoc.mso.MobileSecurityObject
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray

/**
 * Represents a mobile document (mdoc) as presented by a holder's device.
 * This class encapsulates the complete structure of a device response as defined in ISO/IEC 18013-5.
 * It is a friendlier representation of the raw [DeviceResponse].
 *
 * @property documents A list of documents contained in the response.
 * @property documentErrors A map of document types to error codes for documents that could not be returned.
 * @property status The overall status of the response. A value of 0 indicates success.
 * @property version The version of the device response format.
 */
data class Mdoc(
    val documents: List<MdocDocument>,
    val documentErrors: Map<DocumentType, Long>? = null,
    val status: Long,
    val version: String
) {
    /**
     * Convenience property to access the Mobile Security Object (MSO) from the first document.
     * It decodes the payload of the issuerAuth COSE_Sign1 structure.
     */
    val mso: MobileSecurityObject? by lazy {
        documents.firstOrNull()?.issuerAuth?.payload?.let { MdocCbor.decodeFromByteArray(it) }
    }

    companion object {
        /**
         * Decodes an Mdoc object from a CBOR byte array representing a DeviceResponse.
         *
         * @param cborBytes The CBOR-encoded DeviceResponse data.
         * @return A parsed and user-friendly Mdoc object.
         */
        fun fromCBOR(cborBytes: ByteArray): Mdoc {
            val deviceResponse = MdocCbor.decodeFromByteArray<DeviceResponse>(cborBytes)
            return Mdoc(
                documents = deviceResponse.documents ?: emptyList(),
                documentErrors = deviceResponse.documentErrors,
                status = deviceResponse.status,
                version = deviceResponse.version
            )
        }
    }
}

/**
 * Represents the raw top-level DeviceResponse structure as defined in ISO/IEC 18013-5, Section 8.3.2.1.2.3.
 * This class is intended for direct CBOR serialization and deserialization.
 */
@Serializable
internal data class DeviceResponse(
    val version: String,
    val documents: List<MdocDocument>? = null,
    val documentErrors: Map<DocumentType, Long>? = null,
    val status: Long,
)
