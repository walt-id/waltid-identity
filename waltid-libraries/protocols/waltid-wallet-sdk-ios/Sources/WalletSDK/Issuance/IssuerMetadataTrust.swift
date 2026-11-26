import Foundation

/// Trusted signer details returned after verifying signed Credential Issuer Metadata.
public struct IssuerMetadataSigner: Equatable, Sendable {
    /// Identifier of the trusted verification key, as reported by the trust resolver.
    public let keyID: String?
    /// JWS algorithm verified by the trust resolver.
    public let algorithm: String
    /// Authority category established by the trust resolver.
    public let trustType: MetadataTrustType

    /// Creates trusted signer details.
    ///
    /// - Parameters:
    ///   - keyID: Identifier of the trusted verification key, as reported by the trust resolver.
    ///   - algorithm: JWS algorithm verified by the trust resolver.
    ///   - trustType: Authority category established by the trust resolver.
    public init(keyID: String?, algorithm: String, trustType: MetadataTrustType) {
        self.keyID = keyID
        self.algorithm = algorithm
        self.trustType = trustType
    }
}

/// Authority category established for a signer of Credential Issuer Metadata.
public enum MetadataTrustType: Equatable, Sendable {
    /// The signer is the trusted credential issuer.
    case trustedIssuer
    /// The signer is a trusted delegate of the credential issuer.
    case trustedDelegate
}

/// Verifies signed Credential Issuer Metadata before the wallet reads its claims.
public protocol IssuerMetadataTrustResolver: Sendable {
    /// Verifies a compact JWS and establishes that its signer is authorized for the expected issuer.
    ///
    /// - Parameters:
    ///   - compactJWT: Compact JWS returned by the Credential Issuer Metadata endpoint.
    ///   - expectedCredentialIssuer: Credential issuer for which signer authority must be established.
    /// - Returns: Trusted signer details used to retain metadata provenance.
    func verify(compactJWT: String, expectedCredentialIssuer: String) async throws -> IssuerMetadataSigner
}
