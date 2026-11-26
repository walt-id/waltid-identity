import Foundation

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
/// Availability of one configured recovery provider, including unmet prerequisites.
public struct WalletRecoveryProviderStatus: Sendable {
    /// Stable configured provider identifier.
    public let id: String
    /// Provider name for presentation.
    public let displayName: String
    /// Current availability; does not confirm cloud delivery.
    public let availability: WalletRecoveryAvailability
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
public protocol IdentityRecoveryProvider: Sendable {
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
public struct IdentityBackupReference: Sendable {
    /// Stable identifier of the owning recovery provider.
    public let providerID: String
    /// Provider-local recovery record identifier.
    public let recordID: String
}

/// Candidates and redacted provider failures from one discovery attempt.
public struct SigningIdentityRecoveryDiscovery: Sendable {
    /// Safe references discovered through available providers.
    public let candidates: [SigningIdentityRecoveryCandidate]
    /// Provider failures from the same discovery attempt.
    public let failures: [SigningIdentityRecoveryProviderFailure]
}

/// A recovery provider that failed while other providers could still be discovered.
public struct SigningIdentityRecoveryProviderFailure: Sendable {
    /// Stable identifier of the configured provider.
    public let providerID: String
    /// Provider name suitable for display.
    public let providerName: String
    /// Stable failure category without private provider diagnostics.
    public let reason: SigningIdentityFailure
    /// Safe explanation, including an unmet provider prerequisite when available.
    public let message: String
}
