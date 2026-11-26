import Foundation

/// Authorization policy for private-key use selected when a wallet key is created.
public enum WalletKeyUseAuthorizationPolicy: Equatable, Sendable {
    /// Ordinary non-interactive private-key operations.
    case none

    /// Strong biometric authentication for every operation; new biometric enrollment invalidates the key.
    case biometricCurrentSet
    /// Accepts newly enrolled strong biometrics. Protected-key access is deferred while biometrics are unavailable.
    /// This policy does not guarantee key recovery.
    case biometricAny
    /// Uses the device credential; zero means authorization for each use.
    case deviceCredential(timeoutSeconds: Int)
    /// Accepts either a strong biometric or the device credential.
    case biometricOrDeviceCredential(timeoutSeconds: Int)

    ///
    /// Strong biometric authentication reusable for a fixed, non-sliding interval after authorization.
    /// iOS retains a per-key LocalAuthentication context; native Keychain metadata cannot independently
    /// verify the interval. Uses the same biometric availability guard as `biometricAny`.
    /// This is recent authentication, not consent for issuance, presentation, or another wallet action.
    case biometricTimedReuse(timeoutSeconds: Int)
}

/// Prompt text supplied to the operating-system-owned authorization UI.
public struct WalletKeyUseAuthorizationPrompt: Equatable, Sendable {
    /// Human-readable reason shown by the platform authorization UI.
    public var message: String
    /// Action label shown for cancelling the platform authorization UI.
    public var cancelText: String

    /// Creates prompt text for protected signing operations.
    ///
    /// - Parameters:
    ///   - message: Human-readable authorization reason.
    ///   - cancelText: Cancellation action label.
    public init(
        message: String = "Please authorize cryptographic signature",
        cancelText: String = "Cancel"
    ) {
        self.message = message
        self.cancelText = cancelText
    }
}

/// Stable protected-key failure reasons exposed by the wallet SDK.
public enum WalletKeyUseAuthorizationFailure: Equatable, Sendable {
    /// The key type or usage set cannot satisfy the selected policy.
    case unsupportedCombination
    /// The platform cannot provide the required biometric capability.
    case biometricUnavailable
    /// No biometric is enrolled on the device.
    case biometricNotEnrolled
    /// The device has no passcode configured.
    case deviceCredentialNotSet
    /// The host application did not provide a usable interaction context.
    case interactionContextUnavailable
    /// The user cancelled or did not complete authorization.
    case authorizationNotCompleted
    /// The protected native key is missing or invalidated.
    case protectedKeyUnavailable
    /// Persisted key metadata is malformed or inconsistent.
    case invalidStoredKeyMetadata
}

/// Stable reasons a protected-key preflight cannot currently be supported.
public enum WalletKeyUseAuthorizationUnsupportedReason: Equatable, Sendable {
    /// The key type or usage set cannot satisfy the selected protected-key policy.
    case unsupportedCombination
    /// The platform cannot provide the required biometric capability.
    case biometricUnavailable
    /// No biometric is enrolled on the device.
    case biometricNotEnrolled
    /// The device has no passcode configured.
    case deviceCredentialNotSet
}

/// Result of checking whether a protected-key request is supported.
public enum WalletKeyUseAuthorizationPreflight: Equatable, Sendable {
    /// The requested protected-key policy can be created with reported timed-reuse metadata.
    case supported(
        effectivePolicy: WalletKeyUseAuthorizationPolicy,
        reuseEnforcement: WalletKeyUseAuthorizationReuseEnforcement?,
        timeoutValidation: WalletKeyUseAuthorizationReuseTimeoutValidation?
    )
    /// The requested policy cannot currently be supported; the reason explains why.
    case unsupported(WalletKeyUseAuthorizationUnsupportedReason)
}

/// Enforcement boundary reported when timed biometric reuse is supported.
public enum WalletKeyUseAuthorizationReuseEnforcement: Equatable, Sendable {
    /// The native key store enforces the authorization validity interval.
    case platformKeyStore

    /// The platform crypto provider reuses process-local authorization state.
    case providerProcess
}

/// Timeout-validation capability reported when timed biometric reuse is supported.
public enum WalletKeyUseAuthorizationReuseTimeoutValidation: Equatable, Sendable {
    /// Native metadata can be independently read back and compared after creation or restoration.
    case independentReadback

    /// The provider receives the interval, but its effective timeout cannot be independently read back.
    case providerConfigurationOnly
}
