import Foundation

/// Runtime wallet-provider integration for OpenID4VCI credential-proof key attestations.
///
/// Supply this dependency again when recreating a wallet. The wallet verifies the returned
/// JWT, but issuer trust and evidence supporting its assurance claims remain provider responsibilities.
public protocol KeyAttestationProvider: KeyAttestationProviderResolver {
    /// Independently obtained public JWK used to verify the provider's signed attestations.
    var verificationPublicJWK: String { get }

    /// Returns a signed key-attestation JWT for the selected proof key and current issuer nonce.
    /// - Parameter request: The actual proof key, issuer nonce and advertised constraints.
    /// - Returns: The compact key-attestation JWT to validate before embedding in the proof.
    func attest(_ request: KeyAttestationRequest) async throws -> String
}

/// Selects the provider and independently trusted verification key for a credential issuer.
///
/// Called only when the selected credential requires key attestation. Return `nil` to reject
/// an unsupported issuer. Supply the resolver again when recreating the wallet.
public protocol KeyAttestationProviderResolver: Sendable {
    /// Resolves a provider without trusting a signer declared in the returned attestation.
    /// - Parameter credentialIssuer: Credential issuer from the resolved issuer metadata.
    /// - Returns: A provider with a fixed verification key, or `nil` for an unsupported issuer.
    func resolve(credentialIssuer: String) async throws -> (any KeyAttestationProvider)?
}

public extension KeyAttestationProvider {
    /// Uses this fixed provider for the issuer, preserving single-provider configurations.
    /// - Parameter credentialIssuer: Credential issuer requesting the attestation.
    /// - Returns: This provider.
    func resolve(credentialIssuer: String) async throws -> (any KeyAttestationProvider)? { self }
}

/// The selected credential proof key and the requirements advertised by its issuer.
public struct KeyAttestationRequest: Sendable {
    /// Credential issuer requesting the proof.
    public let credentialIssuer: String
    /// Selected proof key encoded as a public JWK JSON object.
    public let proofKeyJWK: String
    /// Fresh issuer nonce, when supplied.
    public let nonce: String?
    /// Accepted key-storage values; `nil` means no advertised constraint.
    public let requiredKeyStorage: [String]?
    /// Accepted user-authentication values; `nil` means no advertised constraint.
    public let requiredUserAuthentication: [String]?
}
