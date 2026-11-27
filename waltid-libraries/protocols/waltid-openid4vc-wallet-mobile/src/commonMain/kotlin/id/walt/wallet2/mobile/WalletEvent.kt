package id.walt.wallet2.mobile

import id.walt.wallet2.data.WalletSessionEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * High-level protocol flow that emitted a [WalletEvent].
 */
public enum class WalletEventPhase {
    /** Credential issuance flow. */
    Issuance,

    /** Credential presentation flow. */
    Presentation,
}

/**
 * Progress state represented by a [WalletEvent].
 */
public enum class WalletEventStatus {
    /** The flow is still running. */
    Progress,

    /** The flow completed successfully. */
    Completed,

    /** The flow failed. */
    Failed,
}

/**
 * An event reported by the wallet session.
 *
 * [identifier] is the stable event name emitted by the underlying wallet flow.
 *
 * @property phase Issuance or presentation flow that emitted the event.
 * @property status Current progress state for the event.
 */
public enum class WalletEvent(
    /** Stable event identifier for logging and persistence. */
    public val identifier: String,
    public val phase: WalletEventPhase,
    public val status: WalletEventStatus,
) {
    IssuanceOfferResolved("issuance_offer_resolved", WalletEventPhase.Issuance, WalletEventStatus.Progress),
    IssuanceAttestationObtained("issuance_attestation_obtained", WalletEventPhase.Issuance, WalletEventStatus.Progress),
    IssuanceTokenObtained("issuance_token_obtained", WalletEventPhase.Issuance, WalletEventStatus.Progress),
    IssuanceProofSigned("issuance_proof_signed", WalletEventPhase.Issuance, WalletEventStatus.Progress),
    IssuanceCredentialReceived("issuance_credential_received", WalletEventPhase.Issuance, WalletEventStatus.Progress),
    IssuanceDeferred("issuance_deferred", WalletEventPhase.Issuance, WalletEventStatus.Progress),
    IssuanceCredentialStored("issuance_credential_stored", WalletEventPhase.Issuance, WalletEventStatus.Progress),
    IssuanceCompleted("issuance_completed", WalletEventPhase.Issuance, WalletEventStatus.Completed),
    IssuanceFailed("issuance_failed", WalletEventPhase.Issuance, WalletEventStatus.Failed),
    PresentationRequestParsed("presentation_request_parsed", WalletEventPhase.Presentation, WalletEventStatus.Progress),
    PresentationCredentialsSelected("presentation_credentials_selected", WalletEventPhase.Presentation, WalletEventStatus.Progress),
    PresentationSigned("presentation_signed", WalletEventPhase.Presentation, WalletEventStatus.Progress),
    PresentationResponsePrepared("presentation_response_prepared", WalletEventPhase.Presentation, WalletEventStatus.Progress),
    PresentationSubmitted("presentation_submitted", WalletEventPhase.Presentation, WalletEventStatus.Progress),
    PresentationCompleted("presentation_completed", WalletEventPhase.Presentation, WalletEventStatus.Completed),
    PresentationFailed("presentation_failed", WalletEventPhase.Presentation, WalletEventStatus.Failed),
}

internal class WalletEventStream(
    replay: Int = 10,
    extraBufferCapacity: Int = 32,
) {
    fun tryEmit(event: WalletEvent): Boolean = mutableEvents.tryEmit(event)

    private val mutableEvents = MutableSharedFlow<WalletEvent>(
        replay = replay,
        extraBufferCapacity = extraBufferCapacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: Flow<WalletEvent> = mutableEvents.asSharedFlow()
}

internal fun WalletSessionEvent.toWalletEvent(): WalletEvent =
    when (this) {
        WalletSessionEvent.issuance_offer_resolved -> WalletEvent.IssuanceOfferResolved
        WalletSessionEvent.issuance_attestation_obtained -> WalletEvent.IssuanceAttestationObtained
        WalletSessionEvent.issuance_token_obtained -> WalletEvent.IssuanceTokenObtained
        WalletSessionEvent.issuance_proof_signed -> WalletEvent.IssuanceProofSigned
        WalletSessionEvent.issuance_credential_received -> WalletEvent.IssuanceCredentialReceived
        WalletSessionEvent.issuance_deferred -> WalletEvent.IssuanceDeferred
        WalletSessionEvent.issuance_credential_stored -> WalletEvent.IssuanceCredentialStored
        WalletSessionEvent.issuance_completed -> WalletEvent.IssuanceCompleted
        WalletSessionEvent.issuance_failed -> WalletEvent.IssuanceFailed
        WalletSessionEvent.presentation_request_parsed -> WalletEvent.PresentationRequestParsed
        WalletSessionEvent.presentation_credentials_selected -> WalletEvent.PresentationCredentialsSelected
        WalletSessionEvent.presentation_signed -> WalletEvent.PresentationSigned
        WalletSessionEvent.presentation_response_prepared -> WalletEvent.PresentationResponsePrepared
        WalletSessionEvent.presentation_submitted -> WalletEvent.PresentationSubmitted
        WalletSessionEvent.presentation_completed -> WalletEvent.PresentationCompleted
        WalletSessionEvent.presentation_failed -> WalletEvent.PresentationFailed
    }
