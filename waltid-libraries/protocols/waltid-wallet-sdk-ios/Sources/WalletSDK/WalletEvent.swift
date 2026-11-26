import Foundation

/// Lifecycle event emitted while issuance or presentation work is running.
public enum WalletEvent: CaseIterable, Equatable, Sendable {
    /// Credential offer resolution completed.
    case issuanceOfferResolved
    /// Wallet attestation was obtained.
    case issuanceAttestationObtained
    /// Issuance token was obtained.
    case issuanceTokenObtained
    /// Credential proof was signed.
    case issuanceProofSigned
    /// Credential was received from the issuer.
    case issuanceCredentialReceived
    /// Issuance was deferred by the issuer.
    case issuanceDeferred
    /// Credential was stored locally.
    case issuanceCredentialStored
    /// Issuance completed successfully.
    case issuanceCompleted
    /// Issuance failed.
    case issuanceFailed
    /// Presentation request was parsed.
    case presentationRequestParsed
    /// Presentation credentials were selected.
    case presentationCredentialsSelected
    /// Presentation was signed.
    case presentationSigned
    /// Presentation protocol response was prepared for delivery.
    case presentationResponsePrepared
    /// Presentation was submitted.
    case presentationSubmitted
    /// Presentation completed successfully.
    case presentationCompleted
    /// Presentation failed.
    case presentationFailed

    /// Creates an event from its stable wallet-core name.
    ///
    /// - Parameter name: Stable event name emitted by the wallet core.
    public init?(name: String) {
        guard let event = Self.allCases.first(where: { $0.name == name }) else {
            return nil
        }
        self = event
    }

    /// Stable event name emitted by the wallet core.
    public var name: String {
        switch self {
        case .issuanceOfferResolved: return "issuance_offer_resolved"
        case .issuanceAttestationObtained: return "issuance_attestation_obtained"
        case .issuanceTokenObtained: return "issuance_token_obtained"
        case .issuanceProofSigned: return "issuance_proof_signed"
        case .issuanceCredentialReceived: return "issuance_credential_received"
        case .issuanceDeferred: return "issuance_deferred"
        case .issuanceCredentialStored: return "issuance_credential_stored"
        case .issuanceCompleted: return "issuance_completed"
        case .issuanceFailed: return "issuance_failed"
        case .presentationRequestParsed: return "presentation_request_parsed"
        case .presentationCredentialsSelected: return "presentation_credentials_selected"
        case .presentationSigned: return "presentation_signed"
        case .presentationResponsePrepared: return "presentation_response_prepared"
        case .presentationSubmitted: return "presentation_submitted"
        case .presentationCompleted: return "presentation_completed"
        case .presentationFailed: return "presentation_failed"
        }
    }

    /// High-level workflow phase for the event.
    public var phase: WalletEventPhase {
        switch self {
        case .issuanceOfferResolved,
             .issuanceAttestationObtained,
             .issuanceTokenObtained,
             .issuanceProofSigned,
             .issuanceCredentialReceived,
             .issuanceDeferred,
             .issuanceCredentialStored,
             .issuanceCompleted,
             .issuanceFailed:
            return .issuance
        case .presentationRequestParsed,
             .presentationCredentialsSelected,
             .presentationSigned,
             .presentationResponsePrepared,
             .presentationSubmitted,
             .presentationCompleted,
             .presentationFailed:
            return .presentation
        }
    }

    /// High-level status for the event.
    public var status: WalletEventStatus {
        switch self {
        case .issuanceCompleted, .presentationCompleted:
            return .completed
        case .issuanceFailed, .presentationFailed:
            return .failed
        default:
            return .progress
        }
    }
}

/// High-level workflow phase for a wallet event.
public enum WalletEventPhase: Equatable, Sendable {
    /// Credential issuance workflow.
    case issuance

    /// Credential presentation workflow.
    case presentation
}

/// High-level status for a wallet event.
public enum WalletEventStatus: Equatable, Sendable {
    /// The operation is still running.
    case progress

    /// The operation completed successfully.
    case completed

    /// The operation failed.
    case failed
}
