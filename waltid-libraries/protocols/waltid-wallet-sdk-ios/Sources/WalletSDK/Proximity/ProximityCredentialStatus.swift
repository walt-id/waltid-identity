import Foundation

/// Application-owned status result for a candidate holder credential.
public enum ProximityCredentialStatus: Sendable {
    /// The credential is valid for disclosure.
    case valid
    /// The credential is revoked and must not be disclosed.
    case revoked
    /// The application could not establish credential status.
    case indeterminate
}

/// Credential facts supplied to an application-owned status evaluator.
public struct ProximityCredentialStatusInput: Sendable, Equatable {
    /// Stable wallet credential identifier.
    public let credentialID: String
    /// ISO mdoc document type.
    public let documentType: String
    /// Optional issuer identifier retained by the wallet.
    public let issuer: String?
    /// Credential validity start.
    public let validFrom: Date
    /// Credential validity end.
    public let validUntil: Date
}

/// Explicit status boundary. The SDK itself performs no hidden network lookup.
public protocol ProximityCredentialStatusEvaluator: Sendable {
    /// Evaluates credential status without an implicit SDK lookup.
    /// - Parameter credential: Candidate credential facts.
    /// - Returns: The application's status decision.
    func evaluate(_ credential: ProximityCredentialStatusInput) async throws -> ProximityCredentialStatus
}
