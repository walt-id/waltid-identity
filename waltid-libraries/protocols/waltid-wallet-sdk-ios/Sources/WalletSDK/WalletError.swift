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

    /// A protected key could not be created or used for the stable reason provided.
    case keyUseAuthorization(KeyUseAuthorizationFailure, String)

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
             .keyUseAuthorization(_, let message),
             .credentialNotFound(let message),
             .internalFailure(let message):
            return message
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
