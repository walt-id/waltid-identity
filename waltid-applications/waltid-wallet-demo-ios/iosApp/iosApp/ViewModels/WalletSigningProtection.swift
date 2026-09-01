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
    case unsupportedTimeout(Int)
    case unsupportedPerOperationPolicy

    var errorDescription: String? {
        switch self {
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
    case biometricUnavailable
    case unsupported

    var message: String? {
        switch self {
        case .available: nil
        case .biometricNotEnrolled: "Set up a strong biometric in device settings, then try again."
        case .biometricUnavailable: "Strong biometric authentication is not available on this device."
        case .unsupported: "This signing protection is not supported on this device."
        }
    }

    func warningMessage(canChooseNoBiometricSigning: Bool) -> String? {
        let reason: String
        let recovery: String
        switch self {
        case .available:
            return nil
        case .biometricNotEnrolled:
            reason = "no strong biometric is enrolled"
            recovery = "you enroll a strong biometric"
        case .biometricUnavailable:
            reason = "strong biometric authentication is unavailable"
            recovery = "strong biometric authentication becomes available"
        case .unsupported:
            reason = "the device cannot currently authorize it"
            recovery = "this device can authorize biometric signing"
        }
        let alternative = canChooseNoBiometricSigning
            ? " or you choose no biometric signing in Settings."
            : ". Biometric signing is required by app configuration."
        return "This wallet uses biometric signing, but \(reason). " +
            "Issuance and presentation signing will fail until \(recovery)\(alternative)"
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
