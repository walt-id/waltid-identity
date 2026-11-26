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
    Issuance,

    /** Credential presentation flow. */
    Presentation,
}

/**
 * Progress state represented by a [MobileWalletEvent].
 */
public enum class MobileWalletEventStatus {
    /** The flow is still running. */
    Progress,

    /** The flow completed successfully. */
    Completed,

    /** The flow failed. */
    Failed,
}

/**
 * Observable mobile wallet session event.
 *
 * [identifier] is the stable event name emitted by the underlying wallet flow.
 *
 * @property phase Issuance or presentation flow that emitted the event.
 * @property status Current progress state for the event.
 */
public enum class MobileWalletEvent(
    /** Stable event identifier for logging and persistence. */
    public val identifier: String,
    public val phase: MobileWalletEventPhase,
    public val status: MobileWalletEventStatus,
) {
    IssuanceOfferResolved("issuance_offer_resolved", MobileWalletEventPhase.Issuance, MobileWalletEventStatus.Progress),
    IssuanceAttestationObtained("issuance_attestation_obtained", MobileWalletEventPhase.Issuance, MobileWalletEventStatus.Progress),
    IssuanceTokenObtained("issuance_token_obtained", MobileWalletEventPhase.Issuance, MobileWalletEventStatus.Progress),
    IssuanceProofSigned("issuance_proof_signed", MobileWalletEventPhase.Issuance, MobileWalletEventStatus.Progress),
    IssuanceCredentialReceived("issuance_credential_received", MobileWalletEventPhase.Issuance, MobileWalletEventStatus.Progress),
    IssuanceDeferred("issuance_deferred", MobileWalletEventPhase.Issuance, MobileWalletEventStatus.Progress),
    IssuanceCredentialStored("issuance_credential_stored", MobileWalletEventPhase.Issuance, MobileWalletEventStatus.Progress),
    IssuanceCompleted("issuance_completed", MobileWalletEventPhase.Issuance, MobileWalletEventStatus.Completed),
    IssuanceFailed("issuance_failed", MobileWalletEventPhase.Issuance, MobileWalletEventStatus.Failed),
    PresentationRequestParsed("presentation_request_parsed", MobileWalletEventPhase.Presentation, MobileWalletEventStatus.Progress),
    PresentationCredentialsSelected("presentation_credentials_selected", MobileWalletEventPhase.Presentation, MobileWalletEventStatus.Progress),
    PresentationSigned("presentation_signed", MobileWalletEventPhase.Presentation, MobileWalletEventStatus.Progress),
    PresentationResponsePrepared("presentation_response_prepared", MobileWalletEventPhase.Presentation, MobileWalletEventStatus.Progress),
    PresentationSubmitted("presentation_submitted", MobileWalletEventPhase.Presentation, MobileWalletEventStatus.Progress),
    PresentationCompleted("presentation_completed", MobileWalletEventPhase.Presentation, MobileWalletEventStatus.Completed),
    PresentationFailed("presentation_failed", MobileWalletEventPhase.Presentation, MobileWalletEventStatus.Failed),
}

internal class MobileWalletEventStream(
    replay: Int = 10,
    extraBufferCapacity: Int = 32,
) {
    fun tryEmit(event: MobileWalletEvent): Boolean = mutableEvents.tryEmit(event)

    private val mutableEvents = MutableSharedFlow<MobileWalletEvent>(
        replay = replay,
        extraBufferCapacity = extraBufferCapacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: Flow<MobileWalletEvent> = mutableEvents.asSharedFlow()
}

internal fun WalletSessionEvent.toMobileWalletEvent(): MobileWalletEvent =
    when (this) {
        WalletSessionEvent.issuance_offer_resolved -> MobileWalletEvent.IssuanceOfferResolved
        WalletSessionEvent.issuance_attestation_obtained -> MobileWalletEvent.IssuanceAttestationObtained
        WalletSessionEvent.issuance_token_obtained -> MobileWalletEvent.IssuanceTokenObtained
        WalletSessionEvent.issuance_proof_signed -> MobileWalletEvent.IssuanceProofSigned
        WalletSessionEvent.issuance_credential_received -> MobileWalletEvent.IssuanceCredentialReceived
        WalletSessionEvent.issuance_deferred -> MobileWalletEvent.IssuanceDeferred
        WalletSessionEvent.issuance_credential_stored -> MobileWalletEvent.IssuanceCredentialStored
        WalletSessionEvent.issuance_completed -> MobileWalletEvent.IssuanceCompleted
        WalletSessionEvent.issuance_failed -> MobileWalletEvent.IssuanceFailed
        WalletSessionEvent.presentation_request_parsed -> MobileWalletEvent.PresentationRequestParsed
        WalletSessionEvent.presentation_credentials_selected -> MobileWalletEvent.PresentationCredentialsSelected
        WalletSessionEvent.presentation_signed -> MobileWalletEvent.PresentationSigned
        WalletSessionEvent.presentation_response_prepared -> MobileWalletEvent.PresentationResponsePrepared
        WalletSessionEvent.presentation_submitted -> MobileWalletEvent.PresentationSubmitted
        WalletSessionEvent.presentation_completed -> MobileWalletEvent.PresentationCompleted
        WalletSessionEvent.presentation_failed -> MobileWalletEvent.PresentationFailed
    }
