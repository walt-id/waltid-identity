import Foundation
#if canImport(WalletCore) && os(iOS)
@preconcurrency import WalletCore

private final class KMPIdentityHandle<Value: AnyObject>: WalletIdentityHandle, @unchecked Sendable {
    let value: Value
    init(_ value: Value) { self.value = value }
}

struct KMPWalletIdentityCore: WalletIdentityCore, @unchecked Sendable {
    let bridge: WalletSdkBridge
    private func value<T>(_ result: any WalletBridgeResult, as type: T.Type) throws -> T {
        try KMPWalletCoreBridge.successValue(result, as: type, operation: "identity lifecycle")
    }
    private func handle<T: AnyObject>(_ handle: any WalletIdentityHandle, as type: T.Type) throws -> T {
        guard let value = (handle as? KMPIdentityHandle<T>)?.value else { throw WalletError.internalFailure("Invalid identity option") }
        return value
    }

    func initialize() async throws -> WalletIdentityOperationResult {
        try operation(value(await bridge.initializeIdentity(), as: (any WalletCore.IdentityOperationResult).self))
    }
    func creationOptions(intent: WalletIdentityIntent, attestation: WalletIdentityAttestationRequest) async throws -> WalletIdentityOptions {
        let request: any WalletCore.IdentityAttestationRequest
        switch attestation {
        case .none: request = WalletCore.IdentityAttestationRequestNone.shared
        case .native(let challenge):
            guard (1...128).contains(challenge.count) else { throw WalletError.invalidInput("Native attestation challenge must contain 1 to 128 bytes") }
            request = WalletCore.IdentityAttestationRequestNative(challenge: Waltid_crypto2BinaryData(bytes: challenge.toKotlinByteArray()))
        }
        let options = try value(await bridge.identityCreationOptions(intent: intent == .withoutRecovery ? .withoutRecovery : .recoverable,
                                                                     attestation: request),
                                as: (any WalletCore.IdentityOptions).self)
        switch onEnum(of: options) {
        case .available(let choices): return .available(recommended: creation(choices.recommended), alternatives: choices.alternatives.map(creation))
        case .unavailable(let unavailable): return .unavailable(reasons: unavailable.reasons)
        }
    }
    func create(_ option: WalletIdentityCreationOption) async throws -> WalletIdentityOperationResult {
        try operation(value(await bridge.createIdentity(option: handle(option.handle, as: WalletCore.IdentityCreationOption.self)),
                            as: (any WalletCore.IdentityOperationResult).self))
    }
    func state() async throws -> WalletIdentityState {
        let state = try value(await bridge.identityState(), as: (any WalletCore.WalletIdentityState).self)
        switch onEnum(of: state) {
        case .absent: return .absent
        case .active(let active): return .active(Self.identity(active.identity))
        case .pending(let pending): return .pending(identityID: pending.identityId, reason: failure(pending.reason))
        case .unavailable(let unavailable): return .unavailable(identityID: unavailable.identityId, reason: failure(unavailable.reason))
        }
    }
    func deleteRecovery(_ candidate: WalletIdentityRecoveryCandidate) async throws -> WalletRecoveryReceipt {
        let receipt = try value(await bridge.deleteIdentityRecovery(candidate: handle(candidate.handle, as: WalletCore.RecoveryCandidate.self)),
                                as: WalletCore.RecoveryReceipt.self)
        return receipt == .acceptedLocally ? .acceptedLocally : .confirmedByProvider
    }
    func resumePending(identityID: String) async throws -> WalletIdentityOperationResult {
        try operation(value(await bridge.resumeIdentity(identityId: identityID), as: (any WalletCore.IdentityOperationResult).self))
    }
    func cancelPending(identityID: String) async throws {
        _ = try value(await bridge.cancelPendingIdentity(identityId: identityID), as: Any.self)
    }
    func backupOptions(identityID: String) async throws -> [WalletIdentityBackupOption] {
        try value(await bridge.identityBackupOptions(identityId: identityID), as: [WalletCore.IdentityBackupOption].self).map {
            .init(identityID: $0.identityId, recoveryAvailability: availability($0.recoveryAvailability), providerName: $0.providerName, handle: KMPIdentityHandle($0))
        }
    }
    func backup(_ option: WalletIdentityBackupOption) async throws -> WalletIdentityOperationResult {
        try operation(value(await bridge.backupIdentity(option: handle(option.handle, as: WalletCore.IdentityBackupOption.self)),
                            as: (any WalletCore.IdentityOperationResult).self))
    }
    func custodyOptions(identityID: String) async throws -> [WalletIdentityCustodyOption] {
        try value(await bridge.identityCustodyOptions(identityId: identityID), as: [WalletCore.IdentityCustodyOption].self).map {
            .init(identityID: $0.identityId, custodianName: $0.custodianName, handle: KMPIdentityHandle($0))
        }
    }
    func transferToCustody(_ option: WalletIdentityCustodyOption) async throws -> WalletIdentityCustodyResult {
        let result = try value(await bridge.transferIdentityToCustody(option: handle(option.handle, as: WalletCore.IdentityCustodyOption.self)),
                               as: (any WalletCore.IdentityCustodyResult).self)
        switch onEnum(of: result) {
        case .imported(let imported): return .imported(Self.custodyReference(imported.reference))
        case .failed(let failed): return .failed(failure(failed.reason))
        }
    }
    func recoveryCandidates() async throws -> [WalletIdentityRecoveryCandidate] {
        try value(await bridge.identityRecoveryCandidates(), as: [WalletCore.RecoveryCandidate].self).map {
            .init(reference: Self.reference($0.reference), providerName: $0.providerName, handle: KMPIdentityHandle($0))
        }
    }
    func restorationOptions(_ candidate: WalletIdentityRecoveryCandidate) async throws -> [WalletIdentityRestorationOption] {
        try value(await bridge.identityRestorationOptions(candidate: handle(candidate.handle, as: WalletCore.RecoveryCandidate.self)),
                  as: [WalletCore.IdentityRestorationOption].self).map {
            .init(did: $0.did, storage: Self.storage($0.storage), authorization: toSwiftAuthorizationPolicy($0.authorization), handle: KMPIdentityHandle($0))
        }
    }
    func restore(_ option: WalletIdentityRestorationOption) async throws -> WalletIdentityOperationResult {
        try operation(value(await bridge.restoreIdentity(option: handle(option.handle, as: WalletCore.IdentityRestorationOption.self)),
                            as: (any WalletCore.IdentityOperationResult).self))
    }

    private func creation(_ option: WalletCore.IdentityCreationOption) -> WalletIdentityCreationOption {
        let attestation: WalletIdentityAttestationRequest
        switch onEnum(of: option.attestation) {
        case .none: attestation = .none
        case .native(let request): attestation = .native(challenge: Self.bytes(request.challenge))
        }
        return .init(attestation: attestation, storage: Self.storage(option.storage), authorization: toSwiftAuthorizationPolicy(option.authorization),
                     recoveryProviderName: option.recoveryProviderName, recoveryAvailability: option.recoveryAvailability.map(availability), handle: KMPIdentityHandle(option))
    }
    private func operation(_ result: any WalletCore.IdentityOperationResult) -> WalletIdentityOperationResult {
        switch onEnum(of: result) {
        case .active(let active): return .active(Self.identity(active.identity))
        case .pending(let pending): return .pending(identityID: pending.identityId, reason: failure(pending.reason))
        case .failed(let failed): return .failed(failure(failed.reason))
        }
    }
    fileprivate static func identity(_ value: WalletCore.WalletIdentity) -> WalletIdentity {
        let origin: WalletIdentityKeyOrigin = value.keyFacts.origin == .generated ? .generated : value.keyFacts.origin == .imported ? .imported : .unknown
        let level: WalletIdentitySecurityLevel
        switch value.keyFacts.securityLevel {
        case .software: level = .software
        case .trustedEnvironment: level = .trustedEnvironment
        case .strongbox: level = .strongBox
        case .secureEnclave: level = .secureEnclave
        case .unknown: level = .unknown
        }
        let recovery: WalletIdentityRecoveryState
        switch onEnum(of: value.recovery) {
        case .disabled: recovery = .disabled
        case .removalRequested(let removed): recovery = .removalRequested(reference: reference(removed.reference),
            receipt: removed.receipt == .acceptedLocally ? .acceptedLocally : .confirmedByProvider)
        case .submitted(let submitted): recovery = .submitted(reference: reference(submitted.reference),
            receipt: submitted.receipt == .acceptedLocally ? .acceptedLocally : .confirmedByProvider)
        case .recovered(let restored): recovery = .recovered(reference: reference(restored.reference))
        }
        return .init(id: value.id, keyID: value.keyId, did: value.did, publicJWK: value.publicJwk,
                     storage: storage(value.storage), authorization: toSwiftAuthorizationPolicy(value.authorization),
                     origin: origin, securityLevel: level,
                     attestation: value.keyFacts.attestation.map { .init(format: $0.format,
                         statement: bytes($0.statement), certificateChain: $0.certificateChain.map(bytes)) }, recovery: recovery, custody: value.custody.map(custodyReference))
    }
    private static func custodyReference(_ value: WalletCore.IdentityCustodyReference) -> WalletIdentityCustodyReference {
        .init(custodianID: value.custodianId, keyReference: value.keyReference)
    }
    private static func bytes(_ value: Waltid_crypto2BinaryData) -> Data {
        let bytes = value.toByteArray()
        return Data((0..<bytes.size).map { UInt8(bitPattern: bytes.get(index: $0)) })
    }
    private static func reference(_ value: WalletCore.IdentityBackupReference) -> WalletIdentityBackupReference {
        .init(providerID: value.providerId, recordID: value.recordId)
    }
    private static func storage(_ value: WalletCore.IdentityKeyStorage) -> WalletIdentityStorage {
        switch value {
        case .hardware: return .hardware
        case .nativeStorage: return .nativeStorage
        case .encryptedDatabase: return .encryptedDatabase
        }
    }
    private func availability(_ value: WalletCore.RecoveryAvailabilityAvailable) -> WalletRecoveryAvailability {
        let protection: WalletRecoveryProtection
        switch value.protection {
        case .operatingSystemProtected: protection = .operatingSystemProtected
        case .operatingSystemEndToEnd: protection = .operatingSystemEndToEnd
        case .applicationEncrypted: protection = .applicationEncrypted
        }
        let scope: WalletRecoveryScope
        switch value.scope {
        case .cloud: scope = .cloud
        case .deviceTransfer: scope = .deviceTransfer
        case .custom: scope = .custom
        }
        return .available(protection: protection, scope: scope)
    }
    private func failure(_ value: WalletCore.IdentityFailure) -> WalletIdentityFailure {
        switch value {
        case .unsupportedPolicy: return .unsupportedPolicy
        case .staleOption: return .staleOption
        case .keyUnavailable: return .keyUnavailable
        case .invalidRecoveryRecord: return .invalidRecoveryRecord
        case .authorizationNotCompleted: return .authorizationNotCompleted
        case .nativeOperationFailed: return .nativeOperationFailed
        case .providerUnavailable: return .providerUnavailable
        case .existingIdentity: return .existingIdentity
        case .providerInteractionRequired: return .providerInteractionRequired
        case .providerRejected: return .providerRejected
        case .providerConflict: return .providerConflict
        case .providerConfirmationPending: return .providerConfirmationPending
        }
    }
}

extension WalletIdentityPolicy {
    func toKMPIdentityPolicy() -> WalletCore.IdentityKeyPolicy {
        switch self {
        case .generalPurpose: return .generalPurpose
        case .deviceBound: return .deviceBound
        case .hardwareGenerated: return .hardwareGenerated
        }
    }
}

extension WalletIdentityConfiguration {
    func toKMPIdentityConfiguration() -> WalletCore.IdentityConfiguration {
        let policy = self.policy.toKMPIdentityPolicy()
        let authorization: any WalletCore.IdentityAuthorization
        switch self.authorization {
        case .walletDefault: authorization = WalletCore.IdentityAuthorizationWalletDefault.shared
        case .explicit(let selected): authorization = WalletCore.IdentityAuthorizationExplicit(policy: selected.toKMPNativeAuthorization())
        }
        let platform: any Waltid_crypto2_signumSignumPlatformPolicy
        if let keychain {
            let accessibility: Waltid_crypto2_signumSignumKeychainAccessibility
            switch keychain.accessibility {
            case .whenUnlocked: accessibility = .whenUnlocked
            case .afterFirstUnlock: accessibility = .afterFirstUnlock
            case .whenUnlockedDeviceOnly: accessibility = .whenUnlockedDeviceOnly
            case .afterFirstUnlockDeviceOnly: accessibility = .afterFirstUnlockDeviceOnly
            case .whenPasscodeSetDeviceOnly: accessibility = .whenPasscodeSetDeviceOnly
            }
            platform = Waltid_crypto2_signumSignumPlatformPolicyIosKeychain(accessibility: accessibility, accessGroup: keychain.accessGroup)
        } else { platform = Waltid_crypto2_signumSignumPlatformPolicyDefault.shared }
        return WalletCore.IdentityConfiguration(recoveryProviders: recoveryProviders.map(KMPRecoveryProvider.init), keyCustodians: keyCustodians.map(KMPCustodian.init), authorization: authorization,
                                                policy: policy, platform: platform,
                                                alternativeAuthorizations: alternativeAuthorizations.map { $0.toKMPNativeAuthorization() },
                                                recoveryConfirmation: recoveryConfirmation == .localAcceptance ? .localAcceptance : .providerConfirmation,
                                                localRecoveryMaterial: localRecoveryMaterial == .retain ? .retain : .discardAfterSubmission)
    }
}

private extension WalletKeyUseAuthorizationPolicy {
    func toKMPNativeAuthorization() -> any Waltid_openid4vc_wallet_persistence_mobileKeyUseAuthorizationPolicy {
        switch self {
        case .none: return Waltid_openid4vc_wallet_persistence_mobileKeyUseAuthorizationPolicyNone.shared
        case .biometricCurrentSet: return Waltid_openid4vc_wallet_persistence_mobileKeyUseAuthorizationPolicyBiometricCurrentSet.shared
        case .biometricAny: return Waltid_openid4vc_wallet_persistence_mobileKeyUseAuthorizationPolicyBiometricAny.shared
        case .biometricTimedReuse(let seconds):
            precondition((1...30).contains(seconds))
            return Waltid_openid4vc_wallet_persistence_mobileKeyUseAuthorizationPolicyBiometricTimedReuse(timeoutSeconds: Int32(seconds))
        case .deviceCredential(let seconds):
            precondition((0...30).contains(seconds))
            return Waltid_openid4vc_wallet_persistence_mobileKeyUseAuthorizationPolicyDeviceCredential(timeoutSeconds: Int32(seconds))
        case .biometricOrDeviceCredential(let seconds):
            precondition((0...30).contains(seconds))
            return Waltid_openid4vc_wallet_persistence_mobileKeyUseAuthorizationPolicyBiometricOrDeviceCredential(timeoutSeconds: Int32(seconds))
        }
    }
}

private func identityProviderCall<T>(_ operation: () async throws -> T) async throws -> T {
    do { return try await operation() }
    catch let error as WalletIdentityProviderError {
        let failure: WalletCore.IdentityProviderFailure
        switch error {
        case .temporarilyUnavailable: failure = .temporarilyUnavailable
        case .interactionRequired: failure = .interactionRequired
        case .rejected: failure = .rejected
        case .conflict: failure = .conflict
        case .confirmationPending: failure = .confirmationPending
        }
        try WalletCore.WalletIdentityProviderErrors.shared.raise(failure: failure)
        // Kotlin Nothing is not imported as Swift Never.
        throw error
    }
}

private final class KMPRecoveryProvider: WalletCore.IdentityRecoveryProvider, @unchecked Sendable {
    private let provider: any WalletIdentityRecoveryProvider
    init(_ provider: any WalletIdentityRecoveryProvider) { self.provider = provider }
    var id: String { provider.id }
    var displayName: String { provider.displayName }
    func __availability() async throws -> any WalletCore.RecoveryAvailability {
        switch try await identityProviderCall({ try await provider.availability() }) {
        case .unavailable(let reason): return WalletCore.RecoveryAvailabilityUnavailable(reason: reason)
        case .available(let protection, let scope):
            let coreProtection: WalletCore.RecoveryProtection
            switch protection {
            case .operatingSystemProtected: coreProtection = .operatingSystemProtected
            case .operatingSystemEndToEnd: coreProtection = .operatingSystemEndToEnd
            case .applicationEncrypted: coreProtection = .applicationEncrypted
            }
            let coreScope: WalletCore.RecoveryScope
            switch scope { case .cloud: coreScope = .cloud; case .deviceTransfer: coreScope = .deviceTransfer; case .custom: coreScope = .custom }
            return WalletCore.RecoveryAvailabilityAvailable(protection: coreProtection, scope: coreScope)
        }
    }
    func __list() async throws -> [String] { try await identityProviderCall { try await provider.list() } }
    func __store(recordId: String, record: WalletCore.IdentityRecoveryData) async throws -> WalletCore.RecoveryReceipt {
        let bytes = record.doCopyBytes()
        let data = Data((0..<bytes.size).map { UInt8(bitPattern: bytes.get(index: $0)) })
        return try await identityProviderCall { try await provider.store(recordID: recordId, data: data) } == .acceptedLocally ? .acceptedLocally : .confirmedByProvider
    }
    func __retrieve(recordId: String) async throws -> WalletCore.IdentityRecoveryData? {
        guard let data = try await identityProviderCall({ try await provider.retrieve(recordID: recordId) }) else { return nil }
        guard (1...4096).contains(data.count) else { throw WalletError.internalFailure("Invalid recovery record size") }
        return WalletCore.IdentityRecoveryData(bytes: data.toKotlinByteArray())
    }
    func __delete(recordId: String) async throws -> WalletCore.RecoveryReceipt {
        try await identityProviderCall { try await provider.delete(recordID: recordId) } == .acceptedLocally ? .acceptedLocally : .confirmedByProvider
    }
}
private final class KMPCustodian: WalletCore.IdentityKeyCustodian, @unchecked Sendable {
    private let custodian: any WalletIdentityKeyCustodian
    init(_ custodian: any WalletIdentityKeyCustodian) { self.custodian = custodian }
    var id: String { custodian.id }
    var displayName: String { custodian.displayName }
    func __importKey(identity: WalletCore.WalletIdentity, privateKey: Waltid_crypto2EncodedKeyJwk) async throws -> WalletCore.IdentityCustodyReceipt {
        let bytes = privateKey.data.toByteArray()
        let data = Data((0..<bytes.size).map { UInt8(bitPattern: bytes.get(index: $0)) })
        let receipt = try await identityProviderCall {
            try await custodian.importKey(identity: KMPWalletIdentityCore.identity(identity), privateJWK: data)
        }
        return .init(keyReference: receipt.keyReference, publicJwk: receipt.publicJWK)
    }
}
#endif
