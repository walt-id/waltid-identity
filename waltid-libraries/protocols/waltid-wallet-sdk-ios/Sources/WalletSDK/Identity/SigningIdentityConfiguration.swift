import Foundation

/// Host constraints for signing identities. These are not certification claims.
public enum SigningIdentityKeyPolicy: Sendable {
    /// Permits recovery when explicitly selected.
    case generalPurpose
    /// Prohibits backup and custody through this manager; does not change key exportability.
    case backupAndCustodyDisabled
    /// Requires observed hardware generation and prohibits recovery export.
    case hardwareGenerated
}
/// Whether identity options retain a recoverable signing secret.
public enum SigningIdentityIntent: Sendable {
    /// Creates a key without a recovery record; this alone does not establish hardware device binding.
    case withoutRecovery
    /// Retains a secret with an explicitly selected recovery provider.
    case recoverable
}
/// Execution/storage destination. Ordinary Keychain storage does not promise hardware execution.
public enum SigningIdentityKeyStorage: Sendable {
    /// Requires hardware execution, verified after creation or import.
    case hardwareBacked
    /// Uses native protected storage without promising hardware execution.
    case nativeStorage
    /// Uses software signing with the key in the encrypted wallet database.
    case encryptedDatabase
}
/// How the private key entered its current signing environment.
public enum KeyOrigin: Sendable {
    /// The key was generated in its current signing environment.
    case generated
    /// The key was imported and its secret existed outside the signing environment.
    case imported
    /// The platform did not establish key origin.
    case unknown
}
/// Observed execution tier; unknown is never a hardware claim.
public enum KeySecurityLevel: Sendable {
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

/// How signing authorization was established, independently of hardware backing or attestation.
public enum WalletKeyAuthorizationEvidence: Sendable {
    /// The provider has not established the authorization policy.
    case unknown
    /// Authorization settings were read from native key attributes.
    case nativeAttributes
    /// Settings come from an SDK creation record bound to the native entry.
    /// iOS does not expose complete authorization flags for independent readback.
    case creationRecord
}

/// Fresh generation evidence request. iOS currently offers no native signing-key attestation.
public enum SigningIdentityAttestationRequest: Sendable {
    /// Requests no native attestation evidence.
    case none
    /// Requests fresh native evidence for a 1–128 byte relying-party challenge.
    /// - Parameter challenge: Fresh challenge verified by the relying party.
    case native(challenge: Data)
}

/// Native evidence for external verification, not an OpenID4VCI key-attestation JWT or certification result.
public struct KeyAttestation: Sendable {
    /// Evidence format identifier used by the native provider.
    public let format: String
    /// Opaque native attestation statement for external verification.
    public let statement: Data
    /// Native certificate chain, in provider order; trust is not implied.
    public let certificateChain: [Data]
}

/// Signing-key accessibility, independent of recovery-record accessibility.
public enum KeychainAccessibility: Sendable {
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
public struct KeychainConfiguration: Sendable {
    /// Accessibility of the operational signing key.
    public var accessibility: KeychainAccessibility
    /// Optional entitled Keychain access group shared with app extensions.
    public var accessGroup: String?
    /// Creates native signing-key storage settings.
    /// - Parameters:
    ///   - accessibility: Required key accessibility and migration scope.
    ///   - accessGroup: Optional entitled Keychain access group.
    public init(accessibility: KeychainAccessibility = .whenUnlockedDeviceOnly, accessGroup: String? = nil) {
        self.accessibility = accessibility
        self.accessGroup = accessGroup
    }
}

/// Retention of the additional encrypted recovery record, separate from the operational signing key.
public enum WalletLocalRecoveryMaterialRetention: Sendable {
    /// Keeps the additional recovery record in the encrypted database.
    case retain
    /// Discards the additional local record after the configured provider-confirmation requirement is met.
    /// Local acceptance does not establish cloud delivery.
    case discardAfterConfirmation
}

/// Inheritance is explicit so `.none` can never be confused with Swift Optional.none.
public enum SigningIdentityAuthorization: Sendable {
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
public struct SigningIdentityConfiguration: Sendable {
    /// Retention of the additional local recovery record.
    public var localRecoveryMaterial: WalletLocalRecoveryMaterialRetention
    /// Minimum backup-delivery evidence required for activation and local-record disposal.
    public var recoveryConfirmation: WalletRecoveryConfirmation
    /// Constraints applied to identity creation and recovery.
    public var policy: SigningIdentityKeyPolicy
    /// The wallet default is inherited unless an explicit policy is selected.
    public var authorization: SigningIdentityAuthorization
    /// Additional policies offered for explicit selection; initialization never selects a weaker alternative.
    public var alternativeAuthorizations: [WalletKeyUseAuthorizationPolicy]
    /// Nil retains the platform provider defaults.
    public var keychain: KeychainConfiguration?
    /// Trusted integrations that receive secret recovery records.
    public var recoveryProviders: [any IdentityRecoveryProvider]
    /// Optional destinations that receive private-key custody, independently of recovery.
    public var keyCustodians: [any IdentityKeyCustodian]
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
    public init(policy: SigningIdentityKeyPolicy = .generalPurpose,
                recoveryConfirmation: WalletRecoveryConfirmation = .localAcceptance,
                localRecoveryMaterial: WalletLocalRecoveryMaterialRetention = .retain,
                authorization: SigningIdentityAuthorization = .walletDefault,
                alternativeAuthorizations: [WalletKeyUseAuthorizationPolicy] = [],
                keychain: KeychainConfiguration? = nil,
                recoveryProviders: [any IdentityRecoveryProvider] = [],
                keyCustodians: [any IdentityKeyCustodian] = []) {
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
