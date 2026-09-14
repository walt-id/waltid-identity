import Foundation

/// Host constraints for signing identities. These are not certification claims.
public enum WalletIdentityPolicy: Sendable {
    /// Permits recovery when explicitly selected.
    case generalPurpose
    /// Prohibits retaining or exporting a signing secret for recovery.
    case deviceBound
    /// Requires observed hardware generation and prohibits recovery export.
    case hardwareGenerated
}
/// Whether identity options retain a recoverable signing secret.
public enum WalletIdentityIntent: Sendable {
    /// Creates a key without a recovery record; this alone does not establish hardware device binding.
    case withoutRecovery
    /// Retains a secret with an explicitly selected recovery provider.
    case recoverable
}
/// Execution/storage destination. Ordinary Keychain storage does not promise hardware execution.
public enum WalletIdentityStorage: Sendable {
    /// Requires hardware execution, verified after creation or import.
    case hardware
    /// Uses native protected storage without promising hardware execution.
    case nativeStorage
    /// Uses software signing with the key in the encrypted wallet database.
    case encryptedDatabase
}
/// How the private key entered its current signing environment.
public enum WalletIdentityKeyOrigin: Sendable {
    /// The key was generated in its current signing environment.
    case generated
    /// The key was imported and its secret existed outside the signing environment.
    case imported
    /// The platform did not establish key origin.
    case unknown
}
/// Observed execution tier; unknown is never a hardware claim.
public enum WalletIdentitySecurityLevel: Sendable {
    /// Signing uses software cryptography.
    case software
    /// Signing executes in an Android trusted execution environment.
    case trustedEnvironment
    /// Signing executes in Android StrongBox.
    case strongBox
    /// Signing executes in Apple Secure Enclave.
    case secureEnclave
    /// The platform did not establish an execution tier.
    case unknown
}

/// Fresh generation evidence request. iOS currently offers no native signing-key attestation.
public enum WalletIdentityAttestationRequest: Sendable {
    /// Requests no native attestation evidence.
    case none
    /// Requests fresh native evidence for a 1–128 byte relying-party challenge.
    /// - Parameter challenge: Fresh challenge verified by the relying party.
    case native(challenge: Data)
}

/// Native evidence for external verification, not an OpenID4VCI key-attestation JWT or certification result.
public struct WalletIdentityKeyAttestation: Sendable {
    /// Evidence format identifier used by the native provider.
    public let format: String
    /// Opaque native attestation statement for external verification.
    public let statement: Data
    /// Native certificate chain, in provider order; trust is not implied.
    public let certificateChain: [Data]
}

/// Signing-key accessibility, independent of recovery-record accessibility.
public enum WalletIdentityKeychainAccessibility: Sendable {
    /// Accessible while unlocked; allows OS migration.
    case whenUnlocked
    /// Accessible after the first unlock following restart; allows OS migration.
    case afterFirstUnlock
    /// Accessible while unlocked on this device only.
    case whenUnlockedDeviceOnly
    /// Accessible after the first unlock on this device only.
    case afterFirstUnlockDeviceOnly
    /// Requires a device passcode and does not migrate.
    case whenPasscodeSetDeviceOnly
}

/// Native iOS key configuration. Unsupported runtime combinations yield unavailable creation options.
public struct WalletIdentityKeychainConfiguration: Sendable {
    /// Accessibility of the operational signing key.
    public var accessibility: WalletIdentityKeychainAccessibility
    /// Optional entitled Keychain access group shared with app extensions.
    public var accessGroup: String?
    /// Creates native signing-key storage settings.
    /// - Parameters:
    ///   - accessibility: Required key accessibility and migration scope.
    ///   - accessGroup: Optional entitled Keychain access group.
    public init(accessibility: WalletIdentityKeychainAccessibility = .whenUnlockedDeviceOnly, accessGroup: String? = nil) {
        precondition(accessGroup == nil || accessGroup?.isEmpty == false)
        self.accessibility = accessibility
        self.accessGroup = accessGroup
    }
}

/// Retention of the additional encrypted recovery record, separate from the operational signing key.
public enum WalletLocalRecoveryMaterialRetention: Sendable {
    /// Keeps the additional recovery record in the encrypted database.
    case retain
    /// Discards the additional local record after verified provider submission.
    case discardAfterSubmission
}

/// Inheritance is explicit so `.none` can never be confused with Swift Optional.none.
public enum WalletIdentityAuthorization: Sendable {
    /// Inherits the wallet default signing authorization.
    case walletDefault
    /// Uses the supplied signing authorization for newly created identities.
    case explicit(WalletKeyUseAuthorizationPolicy)
}

/// Minimum evidence required before backup-dependent activation or disposal of local recovery material.
public enum WalletRecoveryConfirmation: Sendable {
    /// Accepts exact local readback, without claiming remote delivery.
    case localAcceptance
    /// Requires the provider to confirm delivery within its documented scope.
    case providerConfirmation
}

/// Identity lifecycle configuration. Recovery integrations are disabled unless registered explicitly.
public struct WalletIdentityConfiguration: Sendable {
    /// Retention of the additional local recovery record.
    public var localRecoveryMaterial: WalletLocalRecoveryMaterialRetention
    /// Minimum backup-delivery evidence required for activation and local-record disposal.
    public var recoveryConfirmation: WalletRecoveryConfirmation
    /// Constraints applied to identity creation and recovery.
    public var policy: WalletIdentityPolicy
    /// The wallet default is inherited unless an explicit policy is selected.
    public var authorization: WalletIdentityAuthorization
    /// Additional policies offered for explicit selection; initialization never selects a weaker alternative.
    public var alternativeAuthorizations: [WalletKeyUseAuthorizationPolicy]
    /// Nil retains the platform provider defaults.
    public var keychain: WalletIdentityKeychainConfiguration?
    /// Trusted integrations that receive secret recovery records.
    public var recoveryProviders: [any WalletIdentityRecoveryProvider]
    /// Optional destinations that receive private-key custody, independently of recovery.
    public var keyCustodians: [any WalletIdentityKeyCustodian]
    /// Configures identity constraints and explicitly registered recovery integrations.
    /// - Parameters:
    ///   - policy: Creation and export constraints retained with the identity.
    ///   - recoveryConfirmation: Minimum provider evidence for successful backup.
    ///   - localRecoveryMaterial: Retention of the additional encrypted recovery record.
    ///   - authorization: Explicit policy or inheritance from the wallet default.
    ///   - alternativeAuthorizations: Policies offered only for explicit selection.
    ///   - keychain: Optional native signing-key settings.
    ///   - recoveryProviders: Trusted providers that receive recovery secrets.
    ///   - keyCustodians: Trusted destinations receiving additional private-key copies.
    public init(policy: WalletIdentityPolicy = .generalPurpose,
                recoveryConfirmation: WalletRecoveryConfirmation = .localAcceptance,
                localRecoveryMaterial: WalletLocalRecoveryMaterialRetention = .retain,
                authorization: WalletIdentityAuthorization = .walletDefault,
                alternativeAuthorizations: [WalletKeyUseAuthorizationPolicy] = [],
                keychain: WalletIdentityKeychainConfiguration? = nil,
                recoveryProviders: [any WalletIdentityRecoveryProvider] = [],
                keyCustodians: [any WalletIdentityKeyCustodian] = []) {
        self.recoveryConfirmation = recoveryConfirmation
        self.localRecoveryMaterial = localRecoveryMaterial
        self.policy = policy
        self.authorization = authorization
        self.alternativeAuthorizations = alternativeAuthorizations
        self.keychain = keychain
        self.recoveryProviders = recoveryProviders
        self.keyCustodians = keyCustodians
    }
}

/// Protection of a recovery record, separate from signing-key hardware.
public enum WalletRecoveryProtection: Sendable {
    /// Protected by the operating system without an end-to-end assurance claim.
    case operatingSystemProtected
    /// The provider requires OS end-to-end protection to be available.
    case operatingSystemEndToEnd
    /// The application provider supplies its own encryption boundary.
    case applicationEncrypted
}
/// Delivery route configured by the provider, not evidence that delivery completed.
public enum WalletRecoveryScope: Sendable {
    /// Configured for cloud recovery; delivery may be unobservable.
    case cloud
    /// Configured for device transfer without cloud backup.
    case deviceTransfer
    /// Uses an application-defined recovery route.
    case custom
}
/// Current provider prerequisites, rechecked before execution.
public enum WalletRecoveryAvailability: Sendable {
    /// The provider currently meets the stated protection and route prerequisites.
    /// - Parameters:
    ///   - protection: Protection required by the provider.
    ///   - scope: Configured delivery route, not confirmation of delivery.
    case available(protection: WalletRecoveryProtection, scope: WalletRecoveryScope)
    /// The provider is unavailable for the stated reason.
    /// - Parameter reason: User-readable prerequisite failure.
    case unavailable(reason: String)
}
/// OS writes normally acknowledge local submission only.
public enum WalletRecoveryReceipt: Sendable {
    /// The local provider accepted the write or deletion.
    case acceptedLocally
    /// The provider confirmed completion within its documented scope.
    case confirmedByProvider
}

/// Trusted recovery integration. Protect records in transit and at rest, scope them to the intended
/// app/user, and reject overwriting different data under an existing ID. Never log the secret bytes.
public protocol WalletIdentityRecoveryProvider: Sendable {
    /// Stable provider identifier, unique within a wallet configuration.
    var id: String { get }
    /// User-facing provider name.
    var displayName: String { get }
    /// Reports current storage and protection prerequisites.
    func availability() async throws -> WalletRecoveryAvailability
    /// Lists record identifiers scoped to the configured application and user.
    func list() async throws -> [String]
    /// Stores secret recovery bytes idempotently; rejects different bytes under the same identifier.
    /// - Parameters:
    ///   - recordID: Stable record identifier.
    ///   - data: Secret record bytes to protect without logging.
    func store(recordID: String, data: Data) async throws -> WalletRecoveryReceipt
    /// Reads a protected recovery record, or returns nil when absent.
    /// - Parameter recordID: Provider-local record identifier.
    func retrieve(recordID: String) async throws -> Data?
    /// Requests idempotent deletion within the provider scope.
    /// - Parameter recordID: Provider-local record identifier.
    func delete(recordID: String) async throws -> WalletRecoveryReceipt
}

/// Safe reference to a backup. It contains no private key or authorization capability.
public struct WalletIdentityBackupReference: Sendable {
    /// Stable identifier of the owning recovery provider.
    public let providerID: String
    /// Provider-local recovery record identifier.
    public let recordID: String
}
/// Last known recovery action; cloud delivery and remote deletion may remain unknown.
public enum WalletIdentityRecoveryState: Sendable {
    /// No recovery submission is recorded.
    case disabled
    /// A recovery record was submitted.
    /// - Parameters:
    ///   - reference: Submitted record.
    ///   - receipt: Scope of the provider acknowledgment.
    case submitted(reference: WalletIdentityBackupReference, receipt: WalletRecoveryReceipt)
    /// The original identity was restored from this record.
    /// - Parameter reference: Source recovery record.
    case recovered(reference: WalletIdentityBackupReference)
    /// Deletion was requested; copies on other devices may remain.
    /// - Parameters:
    ///   - reference: Record selected for deletion.
    ///   - receipt: Scope of the deletion acknowledgment.
    case removalRequested(reference: WalletIdentityBackupReference, receipt: WalletRecoveryReceipt)
}

/// Public identity details. Seeds, private keys and database keys are never included.
public struct WalletIdentity: Sendable {
    /// Stable identity identifier preserved by recovery.
    public let id: String
    /// Stable wallet key identifier preserved by recovery.
    public let keyID: String
    /// Exact DID associated with this key; restoration never substitutes a DID.
    public let did: String
    /// Public-only JSON Web Key; contains no recovery secret.
    public let publicJWK: String
    /// Selected signing-key storage requirement.
    public let storage: WalletIdentityStorage
    /// Native signing authorization policy.
    public let authorization: WalletKeyUseAuthorizationPolicy
    /// Observed key origin in the signing environment.
    public let origin: WalletIdentityKeyOrigin
    /// Observed signing execution tier.
    public let securityLevel: WalletIdentitySecurityLevel
    /// Optional native evidence; does not establish certification or a key-attestation JWT.
    public let attestation: WalletIdentityKeyAttestation?
    /// Last known recovery action for this identity.
    public let recovery: WalletIdentityRecoveryState
    /// Additional private-key custodians; these references do not establish recoverable backups.
    public let custody: [WalletIdentityCustodyReference]
}

protocol WalletIdentityHandle: Sendable {}

/// SDK-issued option. App code cannot construct or modify executable options.
public struct WalletIdentityCreationOption: Sendable {
    /// Native generation evidence requested for this option.
    public let attestation: WalletIdentityAttestationRequest
    /// Selected signing-key storage requirement.
    public let storage: WalletIdentityStorage
    /// Native signing authorization policy.
    public let authorization: WalletKeyUseAuthorizationPolicy
    /// Recovery provider display name, or nil for a device-bound option.
    public let recoveryProviderName: String?
    /// Provider protection and delivery route, or nil when recovery is disabled.
    public let recoveryAvailability: WalletRecoveryAvailability?
    let handle: any WalletIdentityHandle
}
/// Supported complete choices, or reasons no choice is available.
public enum WalletIdentityOptions: Sendable {
    /// Compatible choices ordered by the configured native preference.
    /// - Parameters:
    ///   - recommended: Preferred supported choice.
    ///   - alternatives: Other explicitly allowed choices.
    case available(recommended: WalletIdentityCreationOption, alternatives: [WalletIdentityCreationOption])
    /// No choice satisfies the current constraints.
    /// - Parameter reasons: Unmet prerequisites.
    case unavailable(reasons: [String])
}
/// SDK-issued backup destination for an existing recoverable identity.
public struct WalletIdentityBackupOption: Sendable {
    /// Identity to which the operation applies.
    public let identityID: String
    /// Provider protection and delivery route, rechecked before submission.
    public let recoveryAvailability: WalletRecoveryAvailability
    /// Display name of the selected recovery provider.
    public let providerName: String
    let handle: any WalletIdentityHandle
}
/// SDK-issued reference to a discovered, not yet validated recovery record.
public struct WalletIdentityRecoveryCandidate: Sendable {
    /// Secret-free provider and record reference.
    public let reference: WalletIdentityBackupReference
    /// Display name of the selected recovery provider.
    public let providerName: String
    let handle: any WalletIdentityHandle
}
/// SDK-issued restoration destination after validating the original key and DID.
public struct WalletIdentityRestorationOption: Sendable {
    /// Exact DID associated with this key; restoration never substitutes a DID.
    public let did: String
    /// Selected signing-key storage requirement.
    public let storage: WalletIdentityStorage
    /// Native signing authorization policy.
    public let authorization: WalletKeyUseAuthorizationPolicy
    let handle: any WalletIdentityHandle
}

/// Stable failure categories. A failure never authorizes a weaker fallback or replacement identity.
public enum WalletIdentityFailure: Sendable {
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
public enum WalletIdentityState: Sendable {
    /// No identity or unassociated signing material is stored.
    case absent
    /// The selected identity is ready for use.
    case active(WalletIdentity)
    /// A journaled operation needs retry or cancellation.
    /// - Parameters:
    ///   - identityID: Identifier of the pending operation.
    ///   - reason: Why the operation needs attention before retry.
    case pending(identityID: String, reason: WalletIdentityFailure = .providerUnavailable)
    /// Established identity state needs attention.
    /// - Parameters:
    ///   - identityID: Selected identity, when known.
    ///   - reason: Failure that prevents use.
    case unavailable(identityID: String?, reason: WalletIdentityFailure)
}
/// Outcome of a state-changing identity operation.
public enum WalletIdentityOperationResult: Sendable {
    /// The selected identity is ready for use.
    case active(WalletIdentity)
    /// A journaled operation needs retry or cancellation.
    /// - Parameters:
    ///   - identityID: Identifier of the pending operation.
    ///   - reason: Why the operation needs attention before retry.
    case pending(identityID: String, reason: WalletIdentityFailure = .providerUnavailable)
    /// The operation failed without selecting a replacement identity.
    case failed(WalletIdentityFailure)
}

/// Creates, backs up and restores the wallet's signing identity through the shared Kotlin lifecycle.
/// iOS recovery uses ordinary Keychain keys; same-key Secure Enclave recovery is not offered.
@available(macOS 10.15, *)
public actor WalletIdentityService {
    private let core: any WalletIdentityCore
    init(core: any WalletIdentityCore) { self.core = core }
    /// Reopens the selected identity, or creates the recommended identity without recovery with configured defaults.
    public func initialize() async throws -> WalletIdentityOperationResult { try await core.initialize() }
    /// Lists complete choices compatible with the current native and recovery prerequisites.
    /// - Parameters:
    ///   - intent: Whether the new identity must have a recovery record.
    ///   - attestation: Optional fresh native generation-evidence request.
    public func creationOptions(intent: WalletIdentityIntent = .withoutRecovery, attestation: WalletIdentityAttestationRequest = .none) async throws -> WalletIdentityOptions {
        try await core.creationOptions(intent: intent, attestation: attestation)
    }
    /// Creates the identity described by an SDK-issued option.
    /// - Parameter option: An unmodified choice issued by this service.
    public func create(_ option: WalletIdentityCreationOption) async throws -> WalletIdentityOperationResult {
        try await core.create(option)
    }
    /// Reads persisted identity state without creating a replacement.
    public func state() async throws -> WalletIdentityState { try await core.state() }
    /// Requests deletion of a discovered recovery record.
    /// - Parameter candidate: Record reference issued by this service.
    public func deleteRecovery(_ candidate: WalletIdentityRecoveryCandidate) async throws -> WalletRecoveryReceipt {
        try await core.deleteRecovery(candidate)
    }
    /// Retries a pending submission or cleans an interrupted key operation.
    /// - Parameter identityID: Identifier from a pending lifecycle result.
    public func resumePending(identityID: String) async throws -> WalletIdentityOperationResult {
        try await core.resumePending(identityID: identityID)
    }
    /// Cancels pending local setup; an already submitted provider record is retained for explicit deletion.
    /// - Parameter identityID: Identifier from a pending lifecycle result.
    public func cancelPending(identityID: String) async throws { try await core.cancelPending(identityID: identityID) }
    /// Lists providers that can back up the existing signing secret.
    /// - Parameter identityID: Active identity identifier.
    public func backupOptions(identityID: String) async throws -> [WalletIdentityBackupOption] {
        try await core.backupOptions(identityID: identityID)
    }
    /// Submits the original signing identity to the selected provider.
    /// - Parameter option: Backup choice issued by this service.
    public func backup(_ option: WalletIdentityBackupOption) async throws -> WalletIdentityOperationResult {
        try await core.backup(option)
    }
    /// Lists explicitly configured custody destinations compatible with this identity's export policy.
    /// - Parameter identityID: Active identity whose original key would be copied.
    public func custodyOptions(identityID: String) async throws -> [WalletIdentityCustodyOption] {
        try await core.custodyOptions(identityID: identityID)
    }
    /// Gives the selected custodian an additional key copy; retains the local key and recovery status.
    /// - Parameter option: Unmodified choice issued by this service.
    public func transferToCustody(_ option: WalletIdentityCustodyOption) async throws -> WalletIdentityCustodyResult {
        try await core.transferToCustody(option)
    }
    /// Lists references to discoverable recovery records without exposing secrets.
    public func recoveryCandidates() async throws -> [WalletIdentityRecoveryCandidate] { try await core.recoveryCandidates() }
    /// Validates a recovery record before offering supported signing destinations.
    /// - Parameter candidate: Recovery reference issued by this service.
    public func restorationOptions(_ candidate: WalletIdentityRecoveryCandidate) async throws -> [WalletIdentityRestorationOption] {
        try await core.restorationOptions(candidate)
    }
    /// Restores the original key, key identifier, and DID using a validated option.
    /// - Parameter option: Restoration choice issued by this service.
    public func restore(_ option: WalletIdentityRestorationOption) async throws -> WalletIdentityOperationResult {
        try await core.restore(option)
    }
}

@available(macOS 10.15, *)
protocol WalletIdentityCore: Sendable {
    func initialize() async throws -> WalletIdentityOperationResult
    func creationOptions(intent: WalletIdentityIntent, attestation: WalletIdentityAttestationRequest) async throws -> WalletIdentityOptions
    func create(_ option: WalletIdentityCreationOption) async throws -> WalletIdentityOperationResult
    func state() async throws -> WalletIdentityState
    func deleteRecovery(_ candidate: WalletIdentityRecoveryCandidate) async throws -> WalletRecoveryReceipt
    func resumePending(identityID: String) async throws -> WalletIdentityOperationResult
    func cancelPending(identityID: String) async throws
    func backupOptions(identityID: String) async throws -> [WalletIdentityBackupOption]
    func backup(_ option: WalletIdentityBackupOption) async throws -> WalletIdentityOperationResult
    func custodyOptions(identityID: String) async throws -> [WalletIdentityCustodyOption]
    func transferToCustody(_ option: WalletIdentityCustodyOption) async throws -> WalletIdentityCustodyResult
    func recoveryCandidates() async throws -> [WalletIdentityRecoveryCandidate]
    func restorationOptions(_ candidate: WalletIdentityRecoveryCandidate) async throws -> [WalletIdentityRestorationOption]
    func restore(_ option: WalletIdentityRestorationOption) async throws -> WalletIdentityOperationResult
}

@available(macOS 10.15, *)
struct UnavailableWalletIdentityCore: WalletIdentityCore {
    func initialize() async throws -> WalletIdentityOperationResult { throw unavailable() }
    private func unavailable() -> WalletError { .internalFailure("Identity lifecycle requires the iOS wallet core") }
    func creationOptions(intent: WalletIdentityIntent, attestation: WalletIdentityAttestationRequest) async throws -> WalletIdentityOptions { throw unavailable() }
    func create(_ option: WalletIdentityCreationOption) async throws -> WalletIdentityOperationResult { throw unavailable() }
    func state() async throws -> WalletIdentityState { throw unavailable() }
    func deleteRecovery(_ candidate: WalletIdentityRecoveryCandidate) async throws -> WalletRecoveryReceipt { throw unavailable() }
    func resumePending(identityID: String) async throws -> WalletIdentityOperationResult { throw unavailable() }
    func cancelPending(identityID: String) async throws { throw unavailable() }
    func backupOptions(identityID: String) async throws -> [WalletIdentityBackupOption] { throw unavailable() }
    func backup(_ option: WalletIdentityBackupOption) async throws -> WalletIdentityOperationResult { throw unavailable() }
    func custodyOptions(identityID: String) async throws -> [WalletIdentityCustodyOption] { throw unavailable() }
    func transferToCustody(_ option: WalletIdentityCustodyOption) async throws -> WalletIdentityCustodyResult { throw unavailable() }
    func recoveryCandidates() async throws -> [WalletIdentityRecoveryCandidate] { throw unavailable() }
    func restorationOptions(_ candidate: WalletIdentityRecoveryCandidate) async throws -> [WalletIdentityRestorationOption] { throw unavailable() }
    func restore(_ option: WalletIdentityRestorationOption) async throws -> WalletIdentityOperationResult { throw unavailable() }
}

/// Structured errors thrown by trusted recovery integrations; never include secret bytes.
public enum WalletIdentityProviderError: Error, Sendable {
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
public protocol WalletIdentityKeyCustodian: Sendable {
    /// Stable integration identifier, unique within one wallet configuration.
    var id: String { get }
    /// Destination label shown before selection.
    var displayName: String { get }
    /// Imports idempotently under the identity's key ID; rejects a different existing key.
    /// - Parameters:
    ///   - identity: Original public identity and stable identifiers.
    ///   - privateJWK: Secret P-256 private JWK; never log it or include it in errors.
    func importKey(identity: WalletIdentity, privateJWK: Data) async throws -> WalletIdentityCustodyReceipt
}

/// Destination evidence checked against the original public key by the SDK.
public struct WalletIdentityCustodyReceipt: Sendable {
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
public struct WalletIdentityCustodyReference: Sendable {
    /// Stable registered custodian identifier.
    public let custodianID: String
    /// Destination key resource reference.
    public let keyReference: String
}

/// SDK-issued custody choice; app code cannot construct or modify executable options.
public struct WalletIdentityCustodyOption: Sendable {
    /// Identity whose original key will be copied.
    public let identityID: String
    /// Display name of the trusted destination.
    public let custodianName: String
    let handle: any WalletIdentityHandle
}

/// Key-custody outcome; the local signing key is retained and recovery status is unaffected.
public enum WalletIdentityCustodyResult: Sendable {
    /// The destination reported the original public key after import.
    /// - Parameter reference: Verified destination key reference.
    case imported(WalletIdentityCustodyReference)
    /// No verified custody reference was recorded; remote keys are never deleted automatically.
    /// - Parameter reason: Stable failure category.
    case failed(WalletIdentityFailure)
}
