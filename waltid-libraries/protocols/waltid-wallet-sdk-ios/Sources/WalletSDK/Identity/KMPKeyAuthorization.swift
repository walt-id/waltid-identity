import Foundation
#if canImport(WalletCore) && os(iOS)
@preconcurrency import WalletCore

extension WalletKeyType {
    func toKMPKeyType() -> WalletCore.WalletKeyType {
        switch self {
        case .ed25519:
            return .ed25519
        case .secp256k1:
            return .secp256k1
        case .secp256r1:
            return .secp256r1
        case .secp384r1:
            return .secp384r1
        case .secp521r1:
            return .secp521r1
        case .rsa:
            return .rsa
        case .rsa3072:
            return .rsa3072
        case .rsa4096:
            return .rsa4096
        }
    }
}

extension WalletKeyUseAuthorizationPolicy {
    func toKMPAuthorizationPolicy() throws -> WalletBridgeKeyUseAuthorizationPolicy {
        switch self {
        case .none:
            return WalletBridgeKeyUseAuthorizationPolicy(
                type: .none,
                timeoutSeconds: nil
            )
        case .biometricCurrentSet:
            return WalletBridgeKeyUseAuthorizationPolicy(
                type: .biometricCurrentSet,
                timeoutSeconds: nil
            )
        case .biometricAny:
            return WalletBridgeKeyUseAuthorizationPolicy(type: .biometricAny, timeoutSeconds: nil)
        case .deviceCredential(let timeoutSeconds):
            return WalletBridgeKeyUseAuthorizationPolicy(type: .deviceCredential, timeoutSeconds: KotlinInt(int: try checkedAuthorizationSeconds(timeoutSeconds, minimum: 0)))
        case .biometricOrDeviceCredential(let timeoutSeconds):
            return WalletBridgeKeyUseAuthorizationPolicy(type: .biometricOrDeviceCredential, timeoutSeconds: KotlinInt(int: try checkedAuthorizationSeconds(timeoutSeconds, minimum: 0)))
        case .biometricTimedReuse(let timeoutSeconds):
            return WalletBridgeKeyUseAuthorizationPolicy(
                type: .biometricTimedReuse,
                timeoutSeconds: KotlinInt(int: try checkedAuthorizationSeconds(timeoutSeconds, minimum: 1))
            )
        }
    }
}

extension WalletBridgeKeyUseAuthorizationPolicy {
    func toSwiftAuthorizationPolicy() throws -> WalletKeyUseAuthorizationPolicy {
        switch type {
        case .none: return .none
        case .biometricCurrentSet: return .biometricCurrentSet
        case .biometricAny: return .biometricAny
        case .deviceCredential: return .deviceCredential(timeoutSeconds: Int(timeoutSeconds?.intValue ?? 0))
        case .biometricOrDeviceCredential: return .biometricOrDeviceCredential(timeoutSeconds: Int(timeoutSeconds?.intValue ?? 0))
        case .biometricTimedReuse:
            guard let timeoutSeconds else {
                throw WalletError.internalFailure("Timed biometric reuse preflight omitted its timeout")
            }
            return .biometricTimedReuse(timeoutSeconds: Int(timeoutSeconds.intValue))
        }
    }
}

func toSwiftAuthorizationPolicy(
    _ policy: any Waltid_crypto2KeyUseAuthorizationPolicy
) -> WalletKeyUseAuthorizationPolicy {
    switch onEnum(of: policy) {
    case .none:
        return .none
    case .biometricCurrentSet:
        return .biometricCurrentSet
    case .biometricAny: return .biometricAny
    case .deviceCredential(let credential): return .deviceCredential(timeoutSeconds: Int(credential.timeoutSeconds))
    case .biometricOrDeviceCredential(let credential): return .biometricOrDeviceCredential(timeoutSeconds: Int(credential.timeoutSeconds))
    case .biometricTimedReuse(let timedReuse):
        return .biometricTimedReuse(timeoutSeconds: Int(timedReuse.timeoutSeconds))
    }
}

extension WalletBridgeKeyUseAuthorizationReuseEnforcement {
    func toSwiftAuthorizationReuseEnforcement() -> WalletKeyUseAuthorizationReuseEnforcement {
        switch self {
        case .platformKeyStore: return .platformKeyStore
        case .providerProcess: return .providerProcess
        }
    }
}

extension WalletBridgeKeyUseAuthorizationReuseTimeoutValidation {
    func toSwiftAuthorizationTimeoutValidation() -> WalletKeyUseAuthorizationReuseTimeoutValidation {
        switch self {
        case .independentReadback: return .independentReadback
        case .providerConfigurationOnly: return .providerConfigurationOnly
        }
    }
}

extension Waltid_crypto2KeyUseAuthorizationUnsupportedReason {
    func toSwiftAuthorizationUnsupportedReason() -> WalletKeyUseAuthorizationUnsupportedReason {
        switch self {
        case .unsupportedCombination: return .unsupportedCombination
        case .biometricUnavailable: return .biometricUnavailable
        case .biometricNotEnrolled: return .biometricNotEnrolled
        case .deviceCredentialNotSet: return .deviceCredentialNotSet
        }
    }
}

extension Waltid_crypto2KeyUseAuthorizationFailure {
    func toSwiftAuthorizationFailure() -> WalletKeyUseAuthorizationFailure {
        switch self {
        case .unsupportedCombination: return .unsupportedCombination
        case .biometricUnavailable: return .biometricUnavailable
        case .biometricNotEnrolled: return .biometricNotEnrolled
        case .deviceCredentialNotSet: return .deviceCredentialNotSet
        case .interactionContextUnavailable: return .interactionContextUnavailable
        case .authorizationNotCompleted: return .authorizationNotCompleted
        case .protectedKeyUnavailable: return .protectedKeyUnavailable
        case .invalidStoredKeyMetadata: return .invalidStoredKeyMetadata
        }
    }
}
#endif
