import Foundation
#if canImport(WalletCore) && os(iOS)
@preconcurrency import WalletCore

private final class KMPSigningIdentityHandle<Value: AnyObject>: SigningIdentityHandle, @unchecked Sendable {
    let value: Value
    init(_ value: Value) { self.value = value }
}

struct KMPSigningIdentityCore: SigningIdentityCore, @unchecked Sendable {
    let bridge: WalletSdkBridge
    private func value<T>(_ result: any WalletBridgeResult, as type: T.Type) throws -> T {
        try KMPWalletCoreBridge.successValue(result, as: type, operation: "identity lifecycle")
    }
    private func handle<T: AnyObject>(_ handle: any SigningIdentityHandle, as type: T.Type) throws -> T {
        guard let value = (handle as? KMPSigningIdentityHandle<T>)?.value else { throw WalletError.internalFailure("Invalid identity option") }
        return value
    }

    func initialize() async throws -> SigningIdentityOperationResult {
        try operation(value(await bridge.initializeSigningIdentity(), as: (any WalletCore.SigningIdentityOperationResult).self))
    }
    func recoveryProviderStatuses() async throws -> [WalletRecoveryProviderStatus] {
        let statuses = try value(await bridge.signingIdentityRecoveryProviderStatuses(), as: [WalletCore.IdentityRecoveryProviderStatus].self)
        return statuses.map { status in
            let state: WalletRecoveryAvailability
            switch onEnum(of: status.availability) {
            case .available(let available): state = availability(available)
            case .unavailable(let unavailable): state = .unavailable(reason: unavailable.reason)
            }
            return .init(id: status.id, displayName: status.displayName, availability: state)
        }
    }
    func creationOptions(intent: SigningIdentityIntent, attestation: SigningIdentityAttestationRequest) async throws -> SigningIdentityCreationOptions {
        let request: any WalletCore.SigningIdentityAttestationRequest
        switch attestation {
        case .none: request = WalletCore.SigningIdentityAttestationRequestNone.shared
        case .native(let challenge):
            guard (1...128).contains(challenge.count) else { throw WalletError.invalidInput("Native attestation challenge must contain 1 to 128 bytes") }
            request = WalletCore.SigningIdentityAttestationRequestNative(challenge: Waltid_crypto2BinaryData(bytes: challenge.toKotlinByteArray()))
        }
        let options = try value(await bridge.signingIdentityCreationOptions(intent: intent == .withoutRecovery ? .withoutRecovery : .recoverable,
                                                                     attestation: request),
                                as: (any WalletCore.SigningIdentityCreationOptions).self)
        switch onEnum(of: options) {
        case .available(let choices): return .available(recommended: creation(choices.recommended), alternatives: choices.alternatives.map(creation))
        case .unavailable(let unavailable): return .unavailable(reasons: unavailable.reasons)
        }
    }
    func create(_ option: SigningIdentityCreationOption) async throws -> SigningIdentityOperationResult {
        try operation(value(await bridge.createSigningIdentity(option: handle(option.handle, as: WalletCore.SigningIdentityCreationOption.self)),
                            as: (any WalletCore.SigningIdentityOperationResult).self))
    }
    func state() async throws -> SigningIdentityState {
        let state = try value(await bridge.signingIdentityState(), as: (any WalletCore.SigningIdentityState).self)
        switch onEnum(of: state) {
        case .absent: return .absent
        case .active(let active): return .active(Self.identity(active.identity))
        case .pending(let pending): return .pending(identityID: pending.identityId, reason: failure(pending.reason))
        case .unavailable(let unavailable): return .unavailable(identityID: unavailable.identityId, reason: failure(unavailable.reason))
        }
    }
    func deleteRecovery(_ candidate: SigningIdentityRecoveryCandidate) async throws -> WalletRecoveryReceipt {
        let receipt = try value(await bridge.deleteSigningIdentityRecovery(candidate: handle(candidate.handle, as: WalletCore.SigningIdentityRecoveryCandidate.self)),
                                as: WalletCore.RecoveryReceipt.self)
        return receipt == .acceptedLocally ? .acceptedLocally : .confirmedByProvider
    }
    func resumePending(identityID: String) async throws -> SigningIdentityOperationResult {
        try operation(value(await bridge.resumeSigningIdentity(identityId: identityID), as: (any WalletCore.SigningIdentityOperationResult).self))
    }
    func cancelPending(identityID: String) async throws {
        _ = try value(await bridge.cancelPendingSigningIdentity(identityId: identityID), as: Any.self)
    }
    func backupOptions(identityID: String) async throws -> [SigningIdentityBackupOption] {
        try value(await bridge.signingIdentityBackupOptions(identityId: identityID), as: [WalletCore.SigningIdentityBackupOption].self).map {
            .init(identityID: $0.identityId, recoveryAvailability: availability($0.recoveryAvailability), providerName: $0.providerName, handle: KMPSigningIdentityHandle($0))
        }
    }
    func backup(_ option: SigningIdentityBackupOption) async throws -> SigningIdentityOperationResult {
        try operation(value(await bridge.backupSigningIdentity(option: handle(option.handle, as: WalletCore.SigningIdentityBackupOption.self)),
                            as: (any WalletCore.SigningIdentityOperationResult).self))
    }
    func custodyOptions(identityID: String) async throws -> [SigningIdentityCustodyOption] {
        try value(await bridge.signingIdentityCustodyOptions(identityId: identityID), as: [WalletCore.SigningIdentityCustodyOption].self).map {
            .init(identityID: $0.identityId, custodianName: $0.custodianName, handle: KMPSigningIdentityHandle($0))
        }
    }
    func copyToCustody(_ option: SigningIdentityCustodyOption) async throws -> SigningIdentityCustodyResult {
        let result = try value(await bridge.doCopySigningIdentityToCustody(option: handle(option.handle, as: WalletCore.SigningIdentityCustodyOption.self)),
                               as: (any WalletCore.SigningIdentityCustodyResult).self)
        switch onEnum(of: result) {
        case .imported(let imported): return .imported(Self.custodyReference(imported.reference))
        case .failed(let failed): return .failed(failure(failed.reason))
        }
    }
    func discoverRecovery() async throws -> SigningIdentityRecoveryDiscovery {
        let discovery = try value(await bridge.signingIdentityRecoveryDiscovery(), as: WalletCore.SigningIdentityRecoveryDiscovery.self)
        return .init(candidates: discovery.candidates.map {
            .init(reference: Self.reference($0.reference), providerName: $0.providerName, handle: KMPSigningIdentityHandle($0))
        }, failures: discovery.failures.map {
            .init(providerID: $0.providerId, providerName: $0.providerName, reason: failure($0.reason), message: $0.message)
        })
    }
    func restorationOptions(_ candidate: SigningIdentityRecoveryCandidate) async throws -> [SigningIdentityRestorationOption] {
        try value(await bridge.signingIdentityRestorationOptions(candidate: handle(candidate.handle, as: WalletCore.SigningIdentityRecoveryCandidate.self)),
                  as: [WalletCore.SigningIdentityRestorationOption].self).map {
            .init(did: $0.did, storage: Self.storage($0.storage), authorization: toSwiftAuthorizationPolicy($0.authorization), handle: KMPSigningIdentityHandle($0))
        }
    }
    func restore(_ option: SigningIdentityRestorationOption) async throws -> SigningIdentityOperationResult {
        try operation(value(await bridge.restoreSigningIdentity(option: handle(option.handle, as: WalletCore.SigningIdentityRestorationOption.self)),
                            as: (any WalletCore.SigningIdentityOperationResult).self))
    }

    private func creation(_ option: WalletCore.SigningIdentityCreationOption) -> SigningIdentityCreationOption {
        let attestation: SigningIdentityAttestationRequest
        switch onEnum(of: option.attestation) {
        case .none: attestation = .none
        case .native(let request): attestation = .native(challenge: Self.bytes(request.challenge))
        }
        return .init(attestation: attestation, storage: Self.storage(option.storage), authorization: toSwiftAuthorizationPolicy(option.authorization),
                     recoveryProviderName: option.recoveryProviderName, recoveryAvailability: option.recoveryAvailability.map(availability), handle: KMPSigningIdentityHandle(option))
    }
    private func operation(_ result: any WalletCore.SigningIdentityOperationResult) -> SigningIdentityOperationResult {
        switch onEnum(of: result) {
        case .active(let active): return .active(Self.identity(active.identity))
        case .pending(let pending): return .pending(identityID: pending.identityId, reason: failure(pending.reason))
        case .failed(let failed): return .failed(failure(failed.reason))
        }
    }
    fileprivate static func identity(_ value: WalletCore.SigningIdentity) -> SigningIdentity {
        let origin: KeyOrigin = value.keyFacts.origin == .generated ? .generated : value.keyFacts.origin == .imported ? .imported : .unknown
        let level: KeySecurityLevel
        switch value.keyFacts.securityLevel {
        case .software: level = .software
        case .trustedEnvironment: level = .trustedEnvironment
        case .strongbox: level = .strongBox
        case .secureEnclave: level = .secureEnclave
        case .unknown: level = .unknown
        }
        let authorizationEvidence: WalletKeyAuthorizationEvidence
        switch value.keyFacts.authorizationEvidence {
        case .unknown: authorizationEvidence = .unknown
        case .nativeAttributes: authorizationEvidence = .nativeAttributes
        case .creationRecord: authorizationEvidence = .creationRecord
        }
        let recovery: SigningIdentityRecoveryState
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
                     origin: origin, securityLevel: level, authorizationEvidence: authorizationEvidence,
                     attestation: value.keyFacts.attestation.map { .init(format: $0.format,
                         statement: bytes($0.statement), certificateChain: $0.certificateChain.map(bytes)) }, recovery: recovery, custody: value.custody.map(custodyReference))
    }
    private static func custodyReference(_ value: WalletCore.IdentityCustodyReference) -> IdentityCustodyReference {
        .init(custodianID: value.custodianId, keyReference: value.keyReference)
    }
    private static func bytes(_ value: Waltid_crypto2BinaryData) -> Data {
        let bytes = value.toByteArray()
        return Data((0..<bytes.size).map { UInt8(bitPattern: bytes.get(index: $0)) })
    }
    private static func reference(_ value: WalletCore.IdentityBackupReference) -> IdentityBackupReference {
        .init(providerID: value.providerId, recordID: value.recordId)
    }
    private static func storage(_ value: WalletCore.SigningIdentityKeyStorage) -> SigningIdentityKeyStorage {
        switch value {
        case .hardwareBacked: return .hardwareBacked
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
    private func failure(_ value: WalletCore.SigningIdentityFailure) -> SigningIdentityFailure {
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

extension SigningIdentityKeyPolicy {
    func toKMPSigningIdentityPolicy() -> WalletCore.SigningIdentityKeyPolicy {
        switch self {
        case .generalPurpose: return .generalPurpose
        case .backupAndCustodyDisabled: return .backupAndCustodyDisabled
        case .hardwareGenerated: return .hardwareGenerated
        }
    }
}

extension SigningIdentityConfiguration {
    func toKMPSigningIdentityConfiguration() -> WalletCore.SigningIdentityConfiguration {
        let policy = self.policy.toKMPSigningIdentityPolicy()
        let authorization: any WalletCore.SigningIdentityAuthorization
        switch self.authorization {
        case .walletDefault: authorization = WalletCore.SigningIdentityAuthorizationWalletDefault.shared
        case .explicit(let selected): authorization = WalletCore.SigningIdentityAuthorizationExplicit(policy: selected.toKMPNativeAuthorization())
        }
        let platform: any Waltid_crypto2PlatformKeyConfiguration
        if let keychain {
            let accessibility: Waltid_crypto2KeychainAccessibility
            switch keychain.accessibility {
            case .whenUnlocked: accessibility = .whenUnlocked
            case .afterFirstUnlock: accessibility = .afterFirstUnlock
            case .whenUnlockedDeviceOnly: accessibility = .whenUnlockedDeviceOnly
            case .afterFirstUnlockDeviceOnly: accessibility = .afterFirstUnlockDeviceOnly
            case .whenPasscodeSetDeviceOnly: accessibility = .whenPasscodeSetDeviceOnly
            }
            platform = Waltid_crypto2PlatformKeyConfigurationIosKeychain(accessibility: accessibility, accessGroup: keychain.accessGroup)
        } else { platform = Waltid_crypto2PlatformKeyConfigurationDefault.shared }
        return WalletCore.SigningIdentityConfiguration(recoveryProviders: recoveryProviders.map(KMPRecoveryProvider.init), keyCustodians: keyCustodians.map(KMPCustodian.init), authorization: authorization,
                                                policy: policy, platform: platform,
                                                alternativeAuthorizations: alternativeAuthorizations.map { $0.toKMPNativeAuthorization() },
                                                recoveryConfirmation: recoveryConfirmation == .localAcceptance ? .localAcceptance : .providerConfirmation,
                                                localRecoveryMaterial: localRecoveryMaterial == .retain ? .retain : .discardAfterConfirmation)
    }
}

private extension WalletKeyUseAuthorizationPolicy {
    func toKMPNativeAuthorization() -> any Waltid_crypto2KeyUseAuthorizationPolicy {
        switch self {
        case .none: return Waltid_crypto2KeyUseAuthorizationPolicyNone.shared
        case .biometricCurrentSet: return Waltid_crypto2KeyUseAuthorizationPolicyBiometricCurrentSet.shared
        case .biometricAny: return Waltid_crypto2KeyUseAuthorizationPolicyBiometricAny.shared
        case .biometricTimedReuse(let seconds):
            precondition((1...30).contains(seconds))
            return Waltid_crypto2KeyUseAuthorizationPolicyBiometricTimedReuse(timeoutSeconds: Int32(seconds))
        case .deviceCredential(let seconds):
            precondition((0...30).contains(seconds))
            return Waltid_crypto2KeyUseAuthorizationPolicyDeviceCredential(timeoutSeconds: Int32(seconds))
        case .biometricOrDeviceCredential(let seconds):
            precondition((0...30).contains(seconds))
            return Waltid_crypto2KeyUseAuthorizationPolicyBiometricOrDeviceCredential(timeoutSeconds: Int32(seconds))
        }
    }
}

private func identityProviderCall<T>(_ operation: () async throws -> T) async throws -> T {
    do { return try await operation() }
    catch let error as IdentityProviderError {
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
    private let provider: any IdentityRecoveryProvider
    init(_ provider: any IdentityRecoveryProvider) { self.provider = provider }
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
    private let custodian: any IdentityKeyCustodian
    init(_ custodian: any IdentityKeyCustodian) { self.custodian = custodian }
    var id: String { custodian.id }
    var displayName: String { custodian.displayName }
    func __importKey(identity: WalletCore.SigningIdentity, privateKey: Waltid_crypto2EncodedKeyJwk) async throws -> WalletCore.IdentityCustodyReceipt {
        let bytes = privateKey.data.toByteArray()
        let data = Data((0..<bytes.size).map { UInt8(bitPattern: bytes.get(index: $0)) })
        let receipt = try await identityProviderCall {
            try await custodian.importKey(identity: KMPSigningIdentityCore.identity(identity), privateJWK: data)
        }
        return .init(keyReference: receipt.keyReference, publicJwk: receipt.publicJWK)
    }
}
#endif
