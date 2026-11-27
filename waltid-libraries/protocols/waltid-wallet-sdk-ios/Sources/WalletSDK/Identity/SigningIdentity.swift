import Foundation

/// Last known recovery action; cloud delivery and remote deletion may remain unknown.
public enum SigningIdentityRecoveryState: Sendable {
    /// No recovery submission is recorded.
    case disabled
    /// A recovery record was submitted.
    /// - Parameters:
    ///   - reference: Submitted record.
    ///   - receipt: Scope of the provider acknowledgment.
    case submitted(reference: IdentityBackupReference, receipt: WalletRecoveryReceipt)
    /// The original identity was restored from this record.
    /// - Parameter reference: Source recovery record.
    case recovered(reference: IdentityBackupReference)
    /// Deletion was requested; copies on other devices may remain.
    /// - Parameters:
    ///   - reference: Record selected for deletion.
    ///   - receipt: Scope of the deletion acknowledgment.
    case removalRequested(reference: IdentityBackupReference, receipt: WalletRecoveryReceipt)
}

/// Public identity details. Seeds, private keys and database keys are never included.
public struct SigningIdentity: Sendable {
    /// Stable identity identifier preserved by recovery.
    public let id: String
    /// Stable wallet key identifier preserved by recovery.
    public let keyID: String
    /// Exact DID associated with this key; restoration never substitutes a DID.
    public let did: String
    /// Public-only JSON Web Key; contains no recovery secret.
    public let publicJWK: String
    /// Selected signing-key storage requirement.
    public let storage: SigningIdentityKeyStorage
    /// Native signing authorization policy.
    public let authorization: WalletKeyUseAuthorizationPolicy
    /// Observed key origin in the signing environment.
    public let origin: KeyOrigin
    /// Observed signing execution tier.
    public let securityLevel: KeySecurityLevel
    /// Evidence used to validate the signing authorization policy; this is not attestation.
    public let authorizationEvidence: WalletKeyAuthorizationEvidence
    /// Optional native evidence; does not establish certification or a key-attestation JWT.
    public let attestation: KeyAttestation?
    /// Last known recovery action for this identity.
    public let recovery: SigningIdentityRecoveryState
    /// Additional private-key custodians; these references do not establish recoverable backups.
    public let custody: [IdentityCustodyReference]
}

/// SDK-issued option. App code cannot construct or modify executable options.
public struct SigningIdentityCreationOption: Sendable {
    /// Native generation evidence requested for this option.
    public let attestation: SigningIdentityAttestationRequest
    /// Selected signing-key storage requirement.
    public let storage: SigningIdentityKeyStorage
    /// Native signing authorization policy.
    public let authorization: WalletKeyUseAuthorizationPolicy
    /// Recovery provider display name, or nil for an option without recovery.
    public let recoveryProviderName: String?
    /// Provider protection and delivery route, or nil when recovery is disabled.
    public let recoveryAvailability: WalletRecoveryAvailability?
    let handle: any SigningIdentityHandle
}

/// Supported complete choices, or reasons no choice is available.
public enum SigningIdentityCreationOptions: Sendable {
    /// Compatible choices ordered by the configured native preference.
    /// - Parameters:
    ///   - recommended: Preferred supported choice.
    ///   - alternatives: Other explicitly allowed choices.
    case available(recommended: SigningIdentityCreationOption, alternatives: [SigningIdentityCreationOption])
    /// No choice satisfies the current constraints.
    /// - Parameter reasons: Unmet prerequisites.
    case unavailable(reasons: [String])
}
/// SDK-issued backup destination for an existing recoverable identity.
public struct SigningIdentityBackupOption: Sendable {
    /// Identity to which the operation applies.
    public let identityID: String
    /// Provider protection and delivery route, rechecked before submission.
    public let recoveryAvailability: WalletRecoveryAvailability
    /// Display name of the selected recovery provider.
    public let providerName: String
    let handle: any SigningIdentityHandle
}
/// SDK-issued reference to a discovered, not yet validated recovery record.
public struct SigningIdentityRecoveryCandidate: Sendable {
    /// Secret-free provider and record reference.
    public let reference: IdentityBackupReference
    /// Display name of the selected recovery provider.
    public let providerName: String
    let handle: any SigningIdentityHandle
}
/// SDK-issued restoration destination after validating the original key and DID.
public struct SigningIdentityRestorationOption: Sendable {
    /// Exact DID associated with this key; restoration never substitutes a DID.
    public let did: String
    /// Selected signing-key storage requirement.
    public let storage: SigningIdentityKeyStorage
    /// Native signing authorization policy.
    public let authorization: WalletKeyUseAuthorizationPolicy
    let handle: any SigningIdentityHandle
}
/// Stable failure categories. A failure never authorizes a weaker fallback or replacement identity.
public enum SigningIdentityFailure: Sendable {
    /// The requested protection cannot be fulfilled.
    case unsupportedPolicy
    /// The option belongs to another instance or its prerequisites changed.
    case staleOption
    /// An established key is missing or unusable.
    case keyUnavailable
    /// The recovery record failed integrity or identity validation.
    case invalidRecoveryRecord
    /// Required signing authorization was cancelled, denied, or unavailable.
    case authorizationNotCompleted
    /// The native key operation failed without authorizing fallback.
    case nativeOperationFailed
    /// The recovery provider could not complete the operation.
    case providerUnavailable
    /// A different identity is already selected or pending.
    case existingIdentity
    /// The provider requires user interaction before retry.
    case providerInteractionRequired
    /// The provider rejected the operation permanently under its current policy.
    case providerRejected
    /// Another record already occupies the destination identifier.
    case providerConflict
    /// Local acceptance has not yet met the required delivery confirmation.
    case providerConfirmationPending
}

/// Persistent identity lifecycle state; unavailable identities are never silently replaced.
public enum SigningIdentityState: Sendable {
    /// No identity or unassociated signing material is stored.
    case absent
    /// The selected identity is ready for use.
    case active(SigningIdentity)
    /// A journaled operation needs retry or cancellation.
    /// - Parameters:
    ///   - identityID: Identifier of the pending operation.
    ///   - reason: Why the operation needs attention before retry.
    case pending(identityID: String, reason: SigningIdentityFailure = .providerUnavailable)
    /// Established identity state needs attention.
    /// - Parameters:
    ///   - identityID: Selected identity, when known.
    ///   - reason: Failure that prevents use.
    case unavailable(identityID: String?, reason: SigningIdentityFailure)
}
/// Outcome of a state-changing identity operation.
public enum SigningIdentityOperationResult: Sendable {
    /// The selected identity is ready for use.
    case active(SigningIdentity)
    /// A journaled operation needs retry or cancellation.
    /// - Parameters:
    ///   - identityID: Identifier of the pending operation.
    ///   - reason: Why the operation needs attention before retry.
    case pending(identityID: String, reason: SigningIdentityFailure = .providerUnavailable)
    /// The operation failed without selecting a replacement identity.
    case failed(SigningIdentityFailure)
}
protocol SigningIdentityHandle: Sendable {}
