import Foundation

/// Structured errors thrown by trusted recovery integrations; never include secret bytes.
public enum IdentityProviderError: Error, Sendable {
    /// Retry after the service becomes available.
    case temporarilyUnavailable
    /// The user must unlock, sign in, or complete another provider interaction.
    case interactionRequired
    /// The provider policy rejects the operation.
    case rejected
    /// A different record already occupies the requested identifier.
    case conflict
    /// Local acceptance has not yet met the required delivery confirmation.
    case confirmationPending
}

/// Explicitly trusted destination receiving a copy of a private signing key, not a recovery record.
public protocol IdentityKeyCustodian: Sendable {
    /// Stable integration identifier, unique within one wallet configuration.
    var id: String { get }
    /// Destination label shown before selection.
    var displayName: String { get }
    /// Imports idempotently under the identity's key ID; rejects a different existing key.
    /// - Parameters:
    ///   - identity: Original public identity and stable identifiers.
    ///   - privateJWK: Secret P-256 private JWK; never log it or include it in errors.
    func importKey(identity: SigningIdentity, privateJWK: Data) async throws -> IdentityCustodyReceipt
}

/// Destination evidence checked against the original public key by the SDK.
public struct IdentityCustodyReceipt: Sendable {
    /// Stable destination key resource reference.
    public let keyReference: String
    /// Public JWK returned by the destination, without private members.
    public let publicJWK: String
    /// Reports destination evidence for SDK validation.
    /// - Parameters:
    ///   - keyReference: Stable destination key resource reference.
    ///   - publicJWK: Public JWK returned after import.
    public init(keyReference: String, publicJWK: String) {
        self.keyReference = keyReference
        self.publicJWK = publicJWK
    }
}

/// Public reference to an additional private-key custodian; it is not a recovery record.
public struct IdentityCustodyReference: Sendable {
    /// Stable registered custodian identifier.
    public let custodianID: String
    /// Destination key resource reference.
    public let keyReference: String
}

/// SDK-issued custody choice; app code cannot construct or modify executable options.
public struct SigningIdentityCustodyOption: Sendable {
    /// Identity whose original key will be copied.
    public let identityID: String
    /// Display name of the trusted destination.
    public let custodianName: String
    let handle: any SigningIdentityHandle
}

/// Key-custody outcome; the local signing key is retained and recovery status is unaffected.
public enum SigningIdentityCustodyResult: Sendable {
    /// The destination reported the original public key after import.
    /// - Parameter reference: Verified destination key reference.
    case imported(IdentityCustodyReference)
    /// No verified custody reference was recorded; remote keys are never deleted automatically.
    /// - Parameter reason: Stable failure category.
    case failed(SigningIdentityFailure)
}
