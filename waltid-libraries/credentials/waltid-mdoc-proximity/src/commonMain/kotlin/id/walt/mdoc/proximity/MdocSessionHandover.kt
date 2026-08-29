package id.walt.mdoc.proximity

import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.handover.NFCHandover

/** Exact handover variant selected before session cryptography is established. */
sealed interface MdocSessionHandover {
    fun createTranscript(deviceEngagementBytes: ImmutableBytes, eReaderKeyBytes: ImmutableBytes): SessionTranscript

    data object Qr : MdocSessionHandover {
        override fun createTranscript(
            deviceEngagementBytes: ImmutableBytes,
            eReaderKeyBytes: ImmutableBytes,
        ): SessionTranscript = SessionTranscript.forQr(deviceEngagementBytes.copy(), eReaderKeyBytes.copy())
    }

    data class NfcConnection(
        val handoverSelect: ImmutableBytes,
        val handoverRequest: ImmutableBytes? = null,
    ) : MdocSessionHandover {
        init {
            require(handoverSelect.size > 0)
            require(handoverRequest == null || handoverRequest.size > 0)
        }

        override fun createTranscript(
            deviceEngagementBytes: ImmutableBytes,
            eReaderKeyBytes: ImmutableBytes,
        ): SessionTranscript = SessionTranscript.forNfc(
            deviceEngagementBytes.copy(),
            eReaderKeyBytes.copy(),
            NFCHandover(handoverSelect.copy(), handoverRequest?.copy()),
        )
    }

    /** Provisional NFCv2 exact handover, deliberately distinct from conventional NFC. */
    data class ProvisionalNfcV2(
        val handoverSelect: ImmutableBytes,
        val handoverRequest: ImmutableBytes,
    ) : MdocSessionHandover {
        init {
            require(handoverSelect.size > 0)
            require(handoverRequest.size > 0)
        }

        override fun createTranscript(
            deviceEngagementBytes: ImmutableBytes,
            eReaderKeyBytes: ImmutableBytes,
        ): SessionTranscript = SessionTranscript.forNfc(
            deviceEngagementBytes.copy(),
            eReaderKeyBytes.copy(),
            NFCHandover(handoverSelect.copy(), handoverRequest.copy()),
        )
    }
}
