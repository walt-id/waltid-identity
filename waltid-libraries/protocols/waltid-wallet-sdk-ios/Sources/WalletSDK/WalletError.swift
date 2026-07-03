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

    /// The requested credential was not found in the wallet.
    case credentialNotFound(String)

    /// The operation was cancelled.
    case cancelled

    /// The SDK encountered an unexpected internal failure.
    case internalFailure(String)
}
