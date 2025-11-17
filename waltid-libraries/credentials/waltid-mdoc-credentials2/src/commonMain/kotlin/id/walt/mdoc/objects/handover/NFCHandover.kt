package id.walt.mdoc.objects.handover

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.CborArray

/**
 * Represents the `NFCHandover` structure, which is a component of the `SessionTranscript`
 * used when device engagement is performed via Near Field Communication (NFC).
 *
 * This structure captures the essential messages from the NFC Forum Connection Handover protocol,
 * which are cryptographically bound to the session to prevent attacks. It is serialized as a CBOR array.
 *
 * @see ISO/IEC 18013-5:xxxx(E), 9.1.5.1 (Session transcript)
 *
 * @property handoverSelect The binary value of the Handover Select Message retrieved by the mdoc reader from the mdoc. This field is mandatory.
 * @property handoverRequest The binary value of the Handover Request Message sent by the mdoc reader to the mdoc. This field is only present during an NFC Negotiated Handover and **must be null** if NFC Static Handover was used.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@CborArray
data class NFCHandover(
    /** Handover Select Message (binary) */
    @ByteString
    val handoverSelect: ByteArray?,

    /** Handover Request Message (binary) - null if NFC Static Handover was used */
    @ByteString
    val handoverRequest: ByteArray?
): BaseHandoverInfo {
    /**
     * Note: `equals` and `hashCode` are manually overridden because the default implementation for a
     * `data class` uses reference equality for `ByteArray` properties. This override ensures
     * content-based equality for the raw message bytes.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NFCHandover) return false

        if (!handoverSelect.contentEquals(other.handoverSelect)) return false
        if (!handoverRequest.contentEquals(other.handoverRequest)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = handoverSelect?.contentHashCode() ?: 0
        result = 31 * result + (handoverRequest?.contentHashCode() ?: 0)
        return result
    }
}
