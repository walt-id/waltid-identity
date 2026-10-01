import Foundation

/// Errors thrown by the public Swift wallet SDK facade.
public enum WalletError: Error, Equatable, Sendable {
    /// The SDK input could not be parsed or validated.
    case invalidInput(String)

    /// Network communication with an issuer, verifier, or wallet backend failed.
    case network(String)

    /// Issuer-side credential issuance failed.
    case issuer(String)

    /// Verifier-side presentation handling failed.
    case verifier(String)

    /// Local wallet storage failed.
    case storage(String)

    /// Key management, signing, or cryptographic processing failed.
    case crypto(String)

    /// A protected wallet-key request failed for a stable, actionable reason.
    case keyUseAuthorization(WalletKeyUseAuthorizationFailure)

    /// Payment review or authorization failed for a stable reason.
    case paymentConsent(PaymentConsentFailure, message: String)

    /// The requested credential was not found in the wallet.
    case credentialNotFound(String)

    /// The operation was cancelled.
    case cancelled

    /// The SDK encountered an unexpected internal failure.
    case internalFailure(String)
}

extension WalletError: LocalizedError {
    /// A localized, user-readable description of the wallet error.
    public var errorDescription: String? {
        switch self {
        case .invalidInput(let message),
             .network(let message),
             .issuer(let message),
             .verifier(let message),
             .storage(let message),
             .crypto(let message),
             .credentialNotFound(let message),
             .internalFailure(let message):
            return message
        case .paymentConsent(_, let message):
            return message
        case .keyUseAuthorization(let failure):
            return "Wallet key authorization failed: \(failure)"
        case .cancelled:
            return "The wallet operation was cancelled."
        }
    }

    /// A localized explanation of why the wallet error occurred.
    public var failureReason: String? {
        nil
    }

    /// A localized recovery suggestion for the wallet error.
    public var recoverySuggestion: String? {
        nil
    }

    /// A localized help anchor for documentation related to the wallet error.
    public var helpAnchor: String? {
        nil
    }
}

/// Stable reasons for refusing payment consent, resolved by the shared wallet core.
public enum PaymentConsentFailure: Equatable, Sendable {
    /// The credential issuer or signature could not be authenticated.
    case untrustedCredential
    /// Required metadata could not be retrieved within the resource limits.
    case metadataUnavailable
    /// The issuer instructions are malformed or incomplete.
    case invalidMetadata
    /// Fetched metadata does not match a supplied integrity reference.
    case integrityMismatch
    /// The schema or inheritance mechanism is not supported.
    case unsupportedSchema
    /// The payment shape, currency or number of authorizations is unsupported.
    case unsupportedPayment
    /// The transaction contains invalid payment values.
    case invalidPayment
    /// No complete preferred-language catalogue is available.
    case missingTranslation
    /// This attempt has no prepared and acknowledged review.
    case consentRequired
    /// The selection or another bound input changed after review.
    case staleConsent
}
