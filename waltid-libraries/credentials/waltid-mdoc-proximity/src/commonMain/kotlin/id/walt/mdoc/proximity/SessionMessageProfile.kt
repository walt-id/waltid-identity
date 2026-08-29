@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.mdoc.proximity

import id.walt.mdoc.objects.session.SessionData
import id.walt.mdoc.objects.session.SessionEstablishment

/** Wire profile selected by the exact winning handover. */
internal sealed interface MdocSessionMessageProfile {
    /** ISO session messages without a `seq` field. */
    data object Conventional : MdocSessionMessageProfile

    /** Provisional NFCv2 messages with direction-local outgoing `seq` values. */
    data object ProvisionalNfcV2 : MdocSessionMessageProfile
}

internal val MdocSessionHandover.sessionMessageProfile: MdocSessionMessageProfile
    get() = when (this) {
        MdocSessionHandover.Qr,
        is MdocSessionHandover.NfcConnection -> MdocSessionMessageProfile.Conventional
        is MdocSessionHandover.ProvisionalNfcV2 -> MdocSessionMessageProfile.ProvisionalNfcV2
    }

/**
 * Direction-local session-message sequence ownership, independent from AES-GCM and APDU counters.
 *
 * Pinned Multipaz 0.100.0 emits ordered NFCv2 values but does not validate inbound ordering. This
 * class therefore makes outgoing behavior deterministic and accepts every structurally valid
 * provisional inbound sequence value. Conventional sessions fail closed if a provisional field
 * crosses the profile.
 */
internal class MdocSessionMessageSequencer(val profile: MdocSessionMessageProfile) {
    private var nextOutgoing: ULong = 0u

    fun sequence(message: SessionEstablishment): SessionEstablishment {
        require(message.seq == null) { "Outgoing SessionEstablishment is already sequenced" }
        return message.copy(seq = nextOutgoing())
    }

    fun sequence(message: SessionData): SessionData {
        require(message.seq == null) { "Outgoing SessionData is already sequenced" }
        return message.copy(seq = nextOutgoing())
    }

    fun validateIncoming(message: SessionEstablishment) = validateIncoming(message.seq)

    fun validateIncoming(message: SessionData) = validateIncoming(message.seq)

    private fun nextOutgoing(): UInt? = when (profile) {
        MdocSessionMessageProfile.Conventional -> null
        MdocSessionMessageProfile.ProvisionalNfcV2 -> {
            check(nextOutgoing <= UInt.MAX_VALUE.toULong()) { "NFCv2 outgoing sequence number is exhausted" }
            val current = nextOutgoing.toUInt()
            nextOutgoing++
            current
        }
    }

    private fun validateIncoming(value: UInt?) {
        when (profile) {
            MdocSessionMessageProfile.Conventional -> {
                if (value != null) throw ProximityException(
                    ProximityError.Protocol(
                        "unexpected_session_sequence",
                        "A conventional session message contained an NFCv2 sequence number",
                    )
                )
            }
            MdocSessionMessageProfile.ProvisionalNfcV2 -> Unit
        }
    }
}
