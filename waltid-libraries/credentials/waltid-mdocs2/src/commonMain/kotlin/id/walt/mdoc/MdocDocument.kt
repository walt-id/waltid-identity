@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc

import id.walt.cose.CoseMac0
import id.walt.cose.CoseSign1
import id.walt.mdoc.credsdata.CredentialData
import id.walt.mdoc.credsdata.DeviceNameSpaces
import id.walt.mdoc.credsdata.IssuerSigned
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a single document within an mdoc response, as defined in ISO/IEC 18013-5, Section 8.3.2.1.2.3.
 *
 * @property docType The document type identifier (e.g., "org.iso.18013.5.1.mDL").
 * @property issuerSigned The issuer-signed portion of the document, containing data elements and the MSO.
 * @property deviceSigned The device-signed portion, containing data elements authenticated by the holder's device.
 * @property errors A map of namespace to error codes for data elements that could not be returned.
 */
@Serializable
data class MdocDocument(
    val docType: String,
    val issuerSigned: IssuerSigned,
    val deviceSigned: DeviceSigned,
    val errors: Map<String, Map<String, Long>>? = null,
) {
    /**
     * A convenience property to access all credential data from both issuer-signed and device-signed namespaces.
     */
    val credentialData: CredentialData by lazy {
        CredentialData(
            issuerSigned.nameSpaces.orEmpty(),
            deviceSigned.nameSpaces?.nameSpaces.orEmpty()
        )
    }

    /**
     * A convenience property to directly access the issuer authentication COSE_Sign1 structure.
     */
    val issuerAuth: CoseSign1
        get() = issuerSigned.issuerAuth
}

/**
 * Represents the device-signed portion of an mdoc document.
 * It contains namespaces with data elements and the device authentication structure.
 *
 * @property nameSpaces The CBOR-encoded and wrapped namespaces containing device-signed data elements.
 * @property deviceAuth The device authentication data, which can be either a signature or a MAC.
 */
@Serializable
data class DeviceSigned(
    @SerialName("nameSpaces") val nameSpaces: DeviceNameSpaces? = null,
    val deviceAuth: DeviceAuth,
)

/**
 * Represents the device authentication, which is a choice between a signature or a MAC.
 * This corresponds to the COSE_Sign1 or COSE_Mac0 structure.
 * CDDL definition in ISO/IEC 18013-5, Section 8.3.2.1.2.3
 *
 * @property deviceSignature The device signature (COSE_Sign1).
 * @property deviceMac The device MAC (COSE_Mac0).
 */
@Serializable
data class DeviceAuth(
    val deviceSignature: CoseSign1? = null,
    val deviceMac: CoseMac0? = null,
)
