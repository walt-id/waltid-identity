import Foundation

/// Creates, backs up and restores the wallet's signing identity through the shared Kotlin lifecycle.
/// iOS recovery uses ordinary Keychain keys; same-key Secure Enclave recovery is not offered.
@available(macOS 10.15, *)
public actor SigningIdentityManager {
    /// Reopens the selected identity, or creates the recommended identity without recovery with configured defaults.
    public func initialize() async throws -> SigningIdentityOperationResult { try await core.initialize() }
    /// Reports each configured recovery provider, including unmet prerequisites.
    public func recoveryProviderStatuses() async throws -> [WalletRecoveryProviderStatus] {
        try await core.recoveryProviderStatuses()
    }
    /// Lists complete choices compatible with the current native and recovery prerequisites.
    /// - Parameters:
    ///   - intent: Whether the new identity must have a recovery record.
    ///   - attestation: Optional fresh native generation-evidence request.
    public func creationOptions(intent: SigningIdentityIntent = .withoutRecovery, attestation: SigningIdentityAttestationRequest = .none) async throws -> SigningIdentityCreationOptions {
        try await core.creationOptions(intent: intent, attestation: attestation)
    }
    /// Creates the identity described by an SDK-issued option.
    /// - Parameter option: An unmodified choice issued by this service.
    public func create(_ option: SigningIdentityCreationOption) async throws -> SigningIdentityOperationResult {
        try await core.create(option)
    }
    /// Reads persisted identity state without creating a replacement.
    public func state() async throws -> SigningIdentityState { try await core.state() }
    /// Requests deletion of a discovered recovery record.
    /// - Parameter candidate: Record reference issued by this service.
    public func deleteRecovery(_ candidate: SigningIdentityRecoveryCandidate) async throws -> WalletRecoveryReceipt {
        try await core.deleteRecovery(candidate)
    }
    /// Retries a pending submission or cleans an interrupted key operation.
    /// - Parameter identityID: Identifier from a pending lifecycle result.
    public func resumePending(identityID: String) async throws -> SigningIdentityOperationResult {
        try await core.resumePending(identityID: identityID)
    }
    /// Cancels pending local setup; an already submitted provider record is retained for explicit deletion.
    /// - Parameter identityID: Identifier from a pending lifecycle result.
    public func cancelPending(identityID: String) async throws { try await core.cancelPending(identityID: identityID) }
    /// Lists providers that can back up the existing signing secret.
    /// - Parameter identityID: Active identity identifier.
    public func backupOptions(identityID: String) async throws -> [SigningIdentityBackupOption] {
        try await core.backupOptions(identityID: identityID)
    }
    /// Submits the original signing identity to the selected provider.
    /// - Parameter option: Backup choice issued by this service.
    public func backup(_ option: SigningIdentityBackupOption) async throws -> SigningIdentityOperationResult {
        try await core.backup(option)
    }
    /// Lists explicitly configured custody destinations compatible with this identity's export policy.
    /// - Parameter identityID: Active identity whose original key would be copied.
    public func custodyOptions(identityID: String) async throws -> [SigningIdentityCustodyOption] {
        try await core.custodyOptions(identityID: identityID)
    }
    /// Gives the selected custodian an additional key copy; retains the local key and recovery status.
    /// - Parameter option: Unmodified choice issued by this service.
    public func copyToCustody(_ option: SigningIdentityCustodyOption) async throws -> SigningIdentityCustodyResult {
        try await core.copyToCustody(option)
    }
    /// Lists references to discoverable recovery records without exposing secrets.
    public func discoverRecovery() async throws -> SigningIdentityRecoveryDiscovery { try await core.discoverRecovery() }
    /// Validates a recovery record before offering supported signing destinations.
    /// - Parameter candidate: Recovery reference issued by this service.
    public func restorationOptions(_ candidate: SigningIdentityRecoveryCandidate) async throws -> [SigningIdentityRestorationOption] {
        try await core.restorationOptions(candidate)
    }
    /// Restores the original key, key identifier, and DID using a validated option.
    /// - Parameter option: Restoration choice issued by this service.
    public func restore(_ option: SigningIdentityRestorationOption) async throws -> SigningIdentityOperationResult {
        try await core.restore(option)
    }
    init(core: any SigningIdentityCore) { self.core = core }
    private let core: any SigningIdentityCore
}

@available(macOS 10.15, *)
protocol SigningIdentityCore: Sendable {
    func initialize() async throws -> SigningIdentityOperationResult
    func recoveryProviderStatuses() async throws -> [WalletRecoveryProviderStatus]
    func creationOptions(intent: SigningIdentityIntent, attestation: SigningIdentityAttestationRequest) async throws -> SigningIdentityCreationOptions
    func create(_ option: SigningIdentityCreationOption) async throws -> SigningIdentityOperationResult
    func state() async throws -> SigningIdentityState
    func deleteRecovery(_ candidate: SigningIdentityRecoveryCandidate) async throws -> WalletRecoveryReceipt
    func resumePending(identityID: String) async throws -> SigningIdentityOperationResult
    func cancelPending(identityID: String) async throws
    func backupOptions(identityID: String) async throws -> [SigningIdentityBackupOption]
    func backup(_ option: SigningIdentityBackupOption) async throws -> SigningIdentityOperationResult
    func custodyOptions(identityID: String) async throws -> [SigningIdentityCustodyOption]
    func copyToCustody(_ option: SigningIdentityCustodyOption) async throws -> SigningIdentityCustodyResult
    func discoverRecovery() async throws -> SigningIdentityRecoveryDiscovery
    func restorationOptions(_ candidate: SigningIdentityRecoveryCandidate) async throws -> [SigningIdentityRestorationOption]
    func restore(_ option: SigningIdentityRestorationOption) async throws -> SigningIdentityOperationResult
}

@available(macOS 10.15, *)
struct UnavailableSigningIdentityCore: SigningIdentityCore {
    func initialize() async throws -> SigningIdentityOperationResult { throw unavailable() }
    func recoveryProviderStatuses() async throws -> [WalletRecoveryProviderStatus] { throw unavailable() }
    func creationOptions(intent: SigningIdentityIntent, attestation: SigningIdentityAttestationRequest) async throws -> SigningIdentityCreationOptions { throw unavailable() }
    func create(_ option: SigningIdentityCreationOption) async throws -> SigningIdentityOperationResult { throw unavailable() }
    func state() async throws -> SigningIdentityState { throw unavailable() }
    func deleteRecovery(_ candidate: SigningIdentityRecoveryCandidate) async throws -> WalletRecoveryReceipt { throw unavailable() }
    func resumePending(identityID: String) async throws -> SigningIdentityOperationResult { throw unavailable() }
    func cancelPending(identityID: String) async throws { throw unavailable() }
    func backupOptions(identityID: String) async throws -> [SigningIdentityBackupOption] { throw unavailable() }
    func backup(_ option: SigningIdentityBackupOption) async throws -> SigningIdentityOperationResult { throw unavailable() }
    func custodyOptions(identityID: String) async throws -> [SigningIdentityCustodyOption] { throw unavailable() }
    func copyToCustody(_ option: SigningIdentityCustodyOption) async throws -> SigningIdentityCustodyResult { throw unavailable() }
    func discoverRecovery() async throws -> SigningIdentityRecoveryDiscovery { throw unavailable() }
    func restorationOptions(_ candidate: SigningIdentityRecoveryCandidate) async throws -> [SigningIdentityRestorationOption] { throw unavailable() }
    func restore(_ option: SigningIdentityRestorationOption) async throws -> SigningIdentityOperationResult { throw unavailable() }
    private func unavailable() -> WalletError { .internalFailure("Identity lifecycle requires the iOS wallet core") }
}
