import CommonCrypto
import Foundation
import Security

enum DemoPinRecordError: Error {
    case randomGenerationFailed
    case derivationFailed
}

protocol DemoPinStore: AnyObject {
    var hasPin: Bool { get }
    var isBiometricUnlockEnabled: Bool { get set }
    func setPin(_ pin: String) async throws
    func verifyPin(_ pin: String) async -> Bool
    func clear()
}

final class InMemoryDemoPinStore: DemoPinStore {
    private var configuredPin: String?
    var isBiometricUnlockEnabled = false

    var hasPin: Bool { configuredPin != nil }

    func setPin(_ pin: String) async throws {
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
    private let randomSalt: () throws -> Data

    init(
        walletID: String,
        defaults: UserDefaults = .standard,
        randomSalt: @escaping () throws -> Data = { try UserDefaultsDemoPinStore.secureRandomSalt() }
    ) {
        self.defaults = defaults
        self.recordKey = "id.walt.walletdemo.pin.\(walletID)"
        self.biometricKey = "id.walt.walletdemo.pin.biometric.\(walletID)"
        self.randomSalt = randomSalt
    }

    var hasPin: Bool { defaults.string(forKey: recordKey) != nil }

    var isBiometricUnlockEnabled: Bool {
        get { defaults.bool(forKey: biometricKey) }
        set { defaults.set(newValue, forKey: biometricKey) }
    }

    func setPin(_ pin: String) async throws {
        let salt = try randomSalt()
        guard salt.count == Self.saltSizeBytes else {
            throw DemoPinRecordError.randomGenerationFailed
        }
        guard let verifier = Self.derive(pin: pin, salt: salt, iterations: Self.iterations) else {
            throw DemoPinRecordError.derivationFailed
        }
        let record = [
            Self.recordVersion,
            String(Self.iterations),
            salt.base64EncodedString(),
            verifier.base64EncodedString(),
        ].joined(separator: Self.recordSeparator)
        defaults.set(record, forKey: recordKey)
    }

    func verifyPin(_ pin: String) async -> Bool {
        guard let record = defaults.string(forKey: recordKey) else { return false }
        let parts = record.split(
            separator: Character(Self.recordSeparator),
            maxSplits: 3,
            omittingEmptySubsequences: false
        ).map(String.init)
        guard parts.count == 4,
              parts[0] == Self.recordVersion,
              let iterations = Int(parts[1]),
              iterations > 0,
              iterations <= Self.maxIterations,
              let salt = Data(base64Encoded: parts[2]),
              let expected = Data(base64Encoded: parts[3]),
              salt.count == Self.saltSizeBytes,
              expected.count == Self.verifierSizeBytes,
              let actual = Self.derive(pin: pin, salt: salt, iterations: iterations) else {
            return false
        }
        return Self.constantTimeEquals(actual, expected)
    }

    func clear() {
        defaults.removeObject(forKey: recordKey)
        defaults.removeObject(forKey: biometricKey)
    }

    static func derive(pin: String, salt: Data, iterations: Int) -> Data? {
        var derived = Data(count: verifierSizeBytes)
        let status = derived.withUnsafeMutableBytes { derivedBytes in
            salt.withUnsafeBytes { saltBytes in
                pin.withCString { pinPointer in
                    CCKeyDerivationPBKDF(
                        CCPBKDFAlgorithm(kCCPBKDF2),
                        pinPointer,
                        pin.utf8.count,
                        saltBytes.bindMemory(to: UInt8.self).baseAddress,
                        salt.count,
                        CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
                        UInt32(iterations),
                        derivedBytes.bindMemory(to: UInt8.self).baseAddress,
                        verifierSizeBytes
                    )
                }
            }
        }
        return status == kCCSuccess ? derived : nil
    }

    private static func secureRandomSalt() throws -> Data {
        var salt = Data(count: saltSizeBytes)
        let status = salt.withUnsafeMutableBytes { bytes in
            guard let baseAddress = bytes.baseAddress else {
                return Int32(errSecParam)
            }
            return SecRandomCopyBytes(kSecRandomDefault, saltSizeBytes, baseAddress)
        }
        guard status == errSecSuccess else {
            throw DemoPinRecordError.randomGenerationFailed
        }
        return salt
    }

    private static func constantTimeEquals(_ lhs: Data, _ rhs: Data) -> Bool {
        let left = [UInt8](lhs)
        let right = [UInt8](rhs)
        var difference = left.count ^ right.count
        let limit = max(left.count, right.count)
        for index in 0..<limit {
            let leftByte = index < left.count ? left[index] : 0
            let rightByte = index < right.count ? right[index] : 0
            difference |= Int(leftByte ^ rightByte)
        }
        return difference == 0
    }

    static let recordVersion = "1"
    static let recordSeparator = ":"
    static let iterations = 210_000
    static let maxIterations = 1_000_000
    static let saltSizeBytes = 16
    static let verifierSizeBytes = 32
}
