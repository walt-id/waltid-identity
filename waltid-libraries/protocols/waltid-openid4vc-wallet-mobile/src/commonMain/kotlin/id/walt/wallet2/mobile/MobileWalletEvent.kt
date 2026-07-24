package id.walt.wallet2.mobile

import id.walt.wallet2.data.WalletSessionEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * High-level protocol flow that emitted a [MobileWalletEvent].
 */
public enum class MobileWalletEventPhase {
    /** Credential issuance flow. */
    issuance,

    /** Credential presentation flow. */
    presentation,
}

/**
 * Progress state represented by a [MobileWalletEvent].
 */
public enum class MobileWalletEventStatus {
    /** The flow is still running. */
    progress,

    /** The flow completed successfully. */
    completed,

    /** The flow failed. */
    failed,
}

/**
 * Observable mobile wallet session event.
 *
 * The enum name is the stable event name emitted by the underlying wallet flow.
 *
 * @property phase Issuance or presentation flow that emitted the event.
 * @property status Current progress state for the event.
 */
public enum class MobileWalletEvent(
    public val phase: MobileWalletEventPhase,
    public val status: MobileWalletEventStatus,
) {
    issuance_offer_resolved(MobileWalletEventPhase.issuance, MobileWalletEventStatus.progress),
    issuance_attestation_obtained(MobileWalletEventPhase.issuance, MobileWalletEventStatus.progress),
    issuance_token_obtained(MobileWalletEventPhase.issuance, MobileWalletEventStatus.progress),
    issuance_proof_signed(MobileWalletEventPhase.issuance, MobileWalletEventStatus.progress),
    issuance_credential_received(MobileWalletEventPhase.issuance, MobileWalletEventStatus.progress),
    issuance_deferred(MobileWalletEventPhase.issuance, MobileWalletEventStatus.progress),
    issuance_credential_stored(MobileWalletEventPhase.issuance, MobileWalletEventStatus.progress),
    issuance_completed(MobileWalletEventPhase.issuance, MobileWalletEventStatus.completed),
    issuance_failed(MobileWalletEventPhase.issuance, MobileWalletEventStatus.failed),
    presentation_request_parsed(MobileWalletEventPhase.presentation, MobileWalletEventStatus.progress),
    presentation_credentials_selected(MobileWalletEventPhase.presentation, MobileWalletEventStatus.progress),
    presentation_signed(MobileWalletEventPhase.presentation, MobileWalletEventStatus.progress),
    presentation_response_prepared(MobileWalletEventPhase.presentation, MobileWalletEventStatus.progress),
    presentation_submitted(MobileWalletEventPhase.presentation, MobileWalletEventStatus.progress),
    presentation_completed(MobileWalletEventPhase.presentation, MobileWalletEventStatus.completed),
    presentation_failed(MobileWalletEventPhase.presentation, MobileWalletEventStatus.failed),
}

internal class MobileWalletEventStream(
    replay: Int = 10,
    extraBufferCapacity: Int = 32,
) {
    private val mutableEvents = MutableSharedFlow<MobileWalletEvent>(
        replay = replay,
        extraBufferCapacity = extraBufferCapacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: Flow<MobileWalletEvent> = mutableEvents.asSharedFlow()

    fun tryEmit(event: MobileWalletEvent): Boolean = mutableEvents.tryEmit(event)
}

internal fun WalletSessionEvent.toMobileWalletEvent(): MobileWalletEvent =
    when (this) {
        WalletSessionEvent.issuance_offer_resolved -> MobileWalletEvent.issuance_offer_resolved
        WalletSessionEvent.issuance_attestation_obtained -> MobileWalletEvent.issuance_attestation_obtained
        WalletSessionEvent.issuance_token_obtained -> MobileWalletEvent.issuance_token_obtained
        WalletSessionEvent.issuance_proof_signed -> MobileWalletEvent.issuance_proof_signed
        WalletSessionEvent.issuance_credential_received -> MobileWalletEvent.issuance_credential_received
        WalletSessionEvent.issuance_deferred -> MobileWalletEvent.issuance_deferred
        WalletSessionEvent.issuance_credential_stored -> MobileWalletEvent.issuance_credential_stored
        WalletSessionEvent.issuance_completed -> MobileWalletEvent.issuance_completed
        WalletSessionEvent.issuance_failed -> MobileWalletEvent.issuance_failed
        WalletSessionEvent.presentation_request_parsed -> MobileWalletEvent.presentation_request_parsed
        WalletSessionEvent.presentation_credentials_selected -> MobileWalletEvent.presentation_credentials_selected
        WalletSessionEvent.presentation_signed -> MobileWalletEvent.presentation_signed
        WalletSessionEvent.presentation_response_prepared -> MobileWalletEvent.presentation_response_prepared
        WalletSessionEvent.presentation_submitted -> MobileWalletEvent.presentation_submitted
        WalletSessionEvent.presentation_completed -> MobileWalletEvent.presentation_completed
        WalletSessionEvent.presentation_failed -> MobileWalletEvent.presentation_failed
    }
