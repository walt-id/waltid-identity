package id.walt.mdoc.objects.document

import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.elements.DeviceNameSpaces
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.CborArray
import kotlinx.serialization.cbor.ValueTags

/**
 * Represents the data structure that is cryptographically signed or MAC'd by the mdoc to prove possession
 * of the device private key. This structure serves as the detached payload for the `DeviceAuth` signature/MAC.
 *
 * It is serialized as a CBOR array and then wrapped in CBOR Tag 24 to form the final `DeviceAuthenticationBytes`.
 *
 * @see ISO/IEC 18013-5:xxxx(E), 9.1.3.4 (Mechanism for mdoc authentication)
 *
 * @property type A fixed string with the value "DeviceAuthentication", identifying the structure's purpose.
 * @property sessionTranscript The complete `SessionTranscript` object from the engagement phase. This cryptographically
 * links the authentication to the specific mdoc-reader session.
 * @property docType The document type (e.g., "org.iso.18013.5.1.mDL") of the document being presented. This must
 * match the `docType` in the parent [MdocDocument] structure.
 * @property namespaces A CBOR-tagged bytestring (`#6.24`) containing the CBOR-encoded `DeviceNameSpaces` map.
 * This includes all data elements being attested to by the device.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@CborArray
data class DeviceAuthentication(
    /** `DeviceAuthentication` */
    val type: String, // Normal text string (not bytestring)

    val sessionTranscript: SessionTranscript,
    /** Same as in [Document.docType] */

    val docType: String,
    /** Same as in [id.walt.mdoc.objects.DeviceSigned.namespaces] */

    @ValueTags(24U)
    val namespaces: ByteStringWrapper<DeviceNameSpaces>,
) {

    companion object {
        val DEVICE_AUTHENTICATION_TYPE = "DeviceAuthentication" // Not a bytestring
    }

}
