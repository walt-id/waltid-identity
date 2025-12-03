package id.walt.mdoc.objects

import id.walt.mdoc.objects.dcapi.DCAPIHandover
import id.walt.mdoc.objects.handover.NFCHandover
import id.walt.mdoc.objects.handover.OpenID4VPHandover
import id.walt.mdoc.objects.handover.isooid4vp.IsoOID4VPHandover
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.CborArray
import kotlinx.serialization.cbor.CborTag.CBOR_ENCODED_DATA
import kotlinx.serialization.cbor.ValueTags

/**
 * Represents the `SessionTranscript`, a critical data structure for ensuring the cryptographic
 * binding between the engagement phase and the data retrieval phase in an mdoc transaction.
 *
 * It is always serialized as a **three-element CBOR array**:
 * 1. `DeviceEngagementBytes` (or null)
 * 2. `EReaderKeyBytes` (or null)
 * 3. `Handover` (which can be one of several structures, or null for QR handover)
 *
 * To handle the multiple valid structures for the `Handover`, this class uses several nullable
 * properties and a private constructor. Instances **must be created** using the provided companion
 * object factory methods (`forNfc`, `forQr`, `forOpenId`, etc.) to ensure a valid state.
 *
 * @see ISO/IEC 18013-5:xxxx(E), 9.1.5.1
 * @see ISO/IEC TS 18013-7:2024(en), Annex B.4.4 & C.5
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@CborArray
@ConsistentCopyVisibility
data class SessionTranscript private constructor(
    // WORKAROUND: These nullable Int properties are a hack to correctly serialize null
    // for the ByteArray properties above in OID4VP/DCAPI flows when using `encodeDefaults = false`.
    // The factory methods correctly set them to null when the corresponding ByteArray should be omitted.
    @ByteString
    @ValueTags(CBOR_ENCODED_DATA)
    val deviceEngagementBytes: ByteArray? = null,

    @ByteString
    @ValueTags(CBOR_ENCODED_DATA)
    val eReaderKeyBytes: ByteArray? = null,

    /** null -> for OID4VP with ISO/IEC 18013-7 or for QR Handover */
    val deviceEngagementBytesOid: Int? = 42,

    /** null -> for OID4VP with ISO/IEC 18013-7 */
    val eReaderKeyBytesOid: Int? = 42,

    /** null this or [nfcHandover] or deviceEngagementBytesOid -> for QR engagement */
    val isooid4VPHandover: IsoOID4VPHandover? = null,
    val oid4VPHandover: OpenID4VPHandover? = null,

    /** null this or [oid4VPHandover] or deviceEngagementBytesOid -> for QR engagement */
    val nfcHandover: NFCHandover? = null,
    val dcapiHandover: DCAPIHandover? = null,
) {
    init {
        // This check validates that only one type of handover is present, or none for QR.
        val handoverCount = listOfNotNull(isooid4VPHandover, oid4VPHandover, nfcHandover, dcapiHandover).size
        val isQrHandover = handoverCount == 0 && deviceEngagementBytes != null && eReaderKeyBytes != null

        // This check is for OID4VP/DCAPI where the first two elements are null
        val isOidOrDcApiHandover = handoverCount == 1 && deviceEngagementBytes == null && eReaderKeyBytes == null

        val isNfcHandover = handoverCount == 1 && nfcHandover != null && deviceEngagementBytes != null && eReaderKeyBytes != null

        require(isQrHandover || isOidOrDcApiHandover || isNfcHandover) {
            "Invalid SessionTranscript state. Use a factory method to construct."
        }
    }

    companion object {

        /** Creates a SessionTranscript for an NFC Handover flow. */
        fun forNfc(
            deviceEngagementBytes: ByteArray,
            eReaderKeyBytes: ByteArray,
            nfcHandover: NFCHandover,
        ): SessionTranscript = SessionTranscript(
            deviceEngagementBytes = deviceEngagementBytes,
            eReaderKeyBytes = eReaderKeyBytes,
            nfcHandover = nfcHandover
        )

        /** Creates a SessionTranscript for an OID4VP Handover flow based on the outdated ISO/IEC TS 18013-7 spec. */
        fun forIsoOpenId(
            handover: IsoOID4VPHandover,
        ): SessionTranscript = SessionTranscript(
            deviceEngagementBytesOid = null,
            eReaderKeyBytesOid = null,
            isooid4VPHandover = handover
        )

        /** Creates a SessionTranscript for an OID4VP Handover flow based on the final OID4VP 1.0 spec. */
        fun forOpenId(
            handover: OpenID4VPHandover,
        ): SessionTranscript = SessionTranscript(
            deviceEngagementBytesOid = null,
            eReaderKeyBytesOid = null,
            oid4VPHandover = handover
        )

        /** Creates a SessionTranscript for a DCAPI Handover flow. */
        /* Can share handover?
        fun forDcApi(
            handover: DCAPIHandover,
        ): SessionTranscript = SessionTranscript(
            deviceEngagementBytesOid = null,
            eReaderKeyBytesOid = null,
            dcapiHandover = handover
        )*/

        /** Creates a SessionTranscript for a QR Code Handover flow (where the Handover element is null). */
        fun forQr(
            deviceEngagementBytes: ByteArray,
            eReaderKeyBytes: ByteArray,
        ): SessionTranscript = SessionTranscript(
            deviceEngagementBytes = deviceEngagementBytes,
            eReaderKeyBytes = eReaderKeyBytes,
            deviceEngagementBytesOid = null
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SessionTranscript) return false

        if (deviceEngagementBytesOid != other.deviceEngagementBytesOid) return false
        if (eReaderKeyBytesOid != other.eReaderKeyBytesOid) return false
        if (!deviceEngagementBytes.contentEquals(other.deviceEngagementBytes)) return false
        if (!eReaderKeyBytes.contentEquals(other.eReaderKeyBytes)) return false
        if (isooid4VPHandover != other.isooid4VPHandover) return false
        if (oid4VPHandover != other.oid4VPHandover) return false
        if (nfcHandover != other.nfcHandover) return false
        if (dcapiHandover != other.dcapiHandover) return false

        return true
    }

    override fun hashCode(): Int {
        var result = deviceEngagementBytesOid ?: 0
        result = 31 * result + (eReaderKeyBytesOid ?: 0)
        result = 31 * result + (deviceEngagementBytes?.contentHashCode() ?: 0)
        result = 31 * result + (eReaderKeyBytes?.contentHashCode() ?: 0)
        result = 31 * result + (isooid4VPHandover?.hashCode() ?: 0)
        result = 31 * result + (oid4VPHandover?.hashCode() ?: 0)
        result = 31 * result + (nfcHandover?.hashCode() ?: 0)
        result = 31 * result + (dcapiHandover?.hashCode() ?: 0)
        return result
    }
}
