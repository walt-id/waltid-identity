package id.walt.mdoc.proximity

import kotlinx.io.bytestring.ByteString
import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.handover.NFCHandover

/** Exact handover variant selected before session cryptography is established. */
sealed interface MdocSessionHandover {
    fun createTranscript(deviceEngagementBytes: ByteString, eReaderKeyBytes: ByteString): SessionTranscript

    data object Qr : MdocSessionHandover {
        override fun createTranscript(
            deviceEngagementBytes: ByteString,
            eReaderKeyBytes: ByteString,
        ): SessionTranscript = SessionTranscript.forQr(deviceEngagementBytes.toByteArray(), eReaderKeyBytes.toByteArray())
    }

    data class NfcConnection(
        val handoverSelect: ByteString,
        val handoverRequest: ByteString? = null,
    ) : MdocSessionHandover {
        init {
            require(handoverSelect.size > 0)
            require(handoverRequest == null || handoverRequest.size > 0)
        }

        override fun createTranscript(
            deviceEngagementBytes: ByteString,
            eReaderKeyBytes: ByteString,
        ): SessionTranscript = SessionTranscript.forNfc(
            deviceEngagementBytes.toByteArray(),
            eReaderKeyBytes.toByteArray(),
            NFCHandover(handoverSelect.toByteArray(), handoverRequest?.toByteArray()),
        )
    }

    /** Provisional NFCv2 exact handover, deliberately distinct from conventional NFC. */
    data class ProvisionalNfcV2(
        val handoverSelect: ByteString,
        val handoverRequest: ByteString,
    ) : MdocSessionHandover {
        init {
            require(handoverSelect.size > 0)
            require(handoverRequest.size > 0)
        }

        override fun createTranscript(
            deviceEngagementBytes: ByteString,
            eReaderKeyBytes: ByteString,
        ): SessionTranscript = SessionTranscript.forNfc(
            deviceEngagementBytes.toByteArray(),
            eReaderKeyBytes.toByteArray(),
            NFCHandover(handoverSelect.toByteArray(), handoverRequest.toByteArray()),
        )
    }
}
