import CryptoKit
import Foundation
import Security

protocol DemoPinStore: AnyObject {
    var hasPin: Bool { get }
    var isBiometricUnlockEnabled: Bool { get set }
    func setPin(_ pin: String) async
    func verifyPin(_ pin: String) async -> Bool
    func clear()
}

final class InMemoryDemoPinStore: DemoPinStore {
    private var configuredPin: String?
    var isBiometricUnlockEnabled = false

    var hasPin: Bool { configuredPin != nil }

    func setPin(_ pin: String) async {
        configuredPin = pin
    }

    func verifyPin(_ pin: String) async -> Bool {
        configuredPin == pin
    }

    func clear() {
        configuredPin = nil
        isBiometricUnlockEnabled = false
    }
}

final class UserDefaultsDemoPinStore: DemoPinStore {
    private let defaults: UserDefaults
    private let recordKey: String
    private let biometricKey: String

    init(walletID: String, defaults: UserDefaults = .standard) {
        self.defaults = defaults
        self.recordKey = "id.walt.walletdemo.pin.\(walletID)"
        self.biometricKey = "id.walt.walletdemo.pin.biometric.\(walletID)"
    }

    var hasPin: Bool { defaults.string(forKey: recordKey) != nil }

    var isBiometricUnlockEnabled: Bool {
        get { defaults.bool(forKey: biometricKey) }
        set { defaults.set(newValue, forKey: biometricKey) }
    }

    func setPin(_ pin: String) async {
        var salt = Data(count: 16)
        salt.withUnsafeMutableBytes { bytes in
            guard let baseAddress = bytes.baseAddress else { return }
            _ = SecRandomCopyBytes(kSecRandomDefault, 16, baseAddress)
        }
        let verifier = Self.derive(pin: pin, salt: salt)
        let record = ["1", salt.base64EncodedString(), verifier.base64EncodedString()].joined(separator: ":")
        defaults.set(record, forKey: recordKey)
    }

    func verifyPin(_ pin: String) async -> Bool {
        guard let record = defaults.string(forKey: recordKey) else { return false }
        let parts = record.split(separator: ":", maxSplits: 2, omittingEmptySubsequences: false).map(String.init)
        guard parts.count == 3,
              parts[0] == "1",
              let salt = Data(base64Encoded: parts[1]),
              let expected = Data(base64Encoded: parts[2]) else {
            return false
        }
        return Self.derive(pin: pin, salt: salt) == expected
    }

    func clear() {
        defaults.removeObject(forKey: recordKey)
        defaults.removeObject(forKey: biometricKey)
    }

    private static func derive(pin: String, salt: Data) -> Data {
        var hash = Data(SHA256.hash(data: Data(pin.utf8) + salt))
        for _ in 1..<10_000 {
            hash = Data(SHA256.hash(data: hash + salt))
        }
        return hash
    }
}
