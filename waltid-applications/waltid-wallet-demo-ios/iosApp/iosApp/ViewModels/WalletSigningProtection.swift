import Foundation
import WalletSDK

enum WalletDemoSigningProtection: String, CaseIterable, Hashable, Sendable {
    case none
    case biometric

    var authorizationPolicy: WalletKeyUseAuthorizationPolicy {
        switch self {
        case .none: .none
        case .biometric: .biometricTimedReuse(timeoutSeconds: 10)
        }
    }

    init(appliedPolicy policy: WalletKeyUseAuthorizationPolicy) throws {
        switch policy {
        case .none: self = .none
        case .biometricTimedReuse(let timeoutSeconds) where timeoutSeconds == 10:
            self = .biometric
        case .biometricTimedReuse(let timeoutSeconds):
            throw WalletDemoSigningProtectionPolicyError.unsupportedTimeout(timeoutSeconds)
        case .biometricCurrentSet:
            throw WalletDemoSigningProtectionPolicyError.unsupportedPerOperationPolicy
        case .biometricAny, .deviceCredential, .biometricOrDeviceCredential:
            throw WalletDemoSigningProtectionPolicyError.unsupportedAuthorizationPolicy
        }
    }

    var title: String {
        switch self {
        case .none: "No biometric signing"
        case .biometric: "Biometric signing"
        }
    }

    var explanation: String {
        switch self {
        case .none: "Private-key operations do not require biometric authorization."
        case .biometric: "Strong biometric authorization can be reused for signing for 10 seconds."
        }
    }
}

private enum WalletDemoSigningProtectionPolicyError: LocalizedError {
    case unsupportedAuthorizationPolicy
    case unsupportedTimeout(Int)
    case unsupportedPerOperationPolicy

    var errorDescription: String? {
        switch self {
        case .unsupportedAuthorizationPolicy:
            "Wallet key uses an authorization policy outside this demo configuration"
        case .unsupportedTimeout(let seconds):
            "Wallet key uses an unsupported biometric signing timeout: \(seconds) seconds"
        case .unsupportedPerOperationPolicy:
            "Wallet key uses an unsupported per-operation biometric signing policy"
        }
    }
}

enum WalletDemoSigningProtectionMode: String, Equatable, Sendable {
    case required
    case optional
    case disabled

    var defaultSelection: WalletDemoSigningProtection {
        switch self {
        case .required, .optional: .biometric
        case .disabled: .none
        }
    }

    func allows(_ protection: WalletDemoSigningProtection) -> Bool {
        switch self {
        case .required: protection == .biometric
        case .optional: true
        case .disabled: protection == .none
        }
    }

    func resolve(_ stored: WalletDemoSigningProtection?) -> WalletDemoSigningProtection {
        guard let stored, allows(stored) else { return defaultSelection }
        return stored
    }
}

enum WalletDemoSigningProtectionAvailability: Equatable, Sendable {
    case available
    case biometricNotEnrolled
    case deviceCredentialNotSet
    case biometricUnavailable
    case unsupported

    var message: String? {
        switch self {
        case .available: nil
        case .biometricNotEnrolled: "Set up a strong biometric in device settings, then try again."
        case .deviceCredentialNotSet: "Set up a device PIN or passcode in settings, then try again."
        case .biometricUnavailable: "Strong biometric authentication is not available on this device."
        case .unsupported: "This signing protection is not supported on this device."
        }
    }

    func warningMessage(canChooseNoBiometricSigning: Bool) -> String? {
        let reason: String
        switch self {
        case .available: return nil
        case .deviceCredentialNotSet:
            return "Set up a device PIN or passcode in settings, then try again. Restoring device security does not restore invalidated signing keys."
        case .biometricNotEnrolled: reason = "no strong biometric is enrolled"
        case .biometricUnavailable: reason = "strong biometric authentication is unavailable"
        case .unsupported: reason = "the device cannot currently authorize it"
        }
        let alternative = canChooseNoBiometricSigning
            ? " To change signing approval, reset the wallet and set up a new key. This removes local credentials."
            : " Biometric signing is required by app configuration."
        return "This wallet uses biometric signing, but \(reason). Check the device's biometric settings. " +
            "A key invalidated by enrollment changes cannot be used again.\(alternative)"
    }
}

protocol WalletDemoSigningProtectionStore {
    func load() -> WalletDemoSigningProtection?
    func save(_ protection: WalletDemoSigningProtection)
}

struct UserDefaultsWalletDemoSigningProtectionStore: WalletDemoSigningProtectionStore {
    let walletID: String
    var defaults: UserDefaults = .standard

    private var key: String { "wallet-signing-protection:\(walletID)" }

    func load() -> WalletDemoSigningProtection? {
        defaults.string(forKey: key).flatMap(WalletDemoSigningProtection.init(rawValue:))
    }

    func save(_ protection: WalletDemoSigningProtection) {
        defaults.set(protection.rawValue, forKey: key)
    }
}

final class InMemoryWalletDemoSigningProtectionStore: WalletDemoSigningProtectionStore {
    private var value: WalletDemoSigningProtection?

    init(_ value: WalletDemoSigningProtection? = nil) {
        self.value = value
    }

    func load() -> WalletDemoSigningProtection? { value }
    func save(_ protection: WalletDemoSigningProtection) { value = protection }
}
