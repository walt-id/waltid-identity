import Foundation
#if canImport(WalletCore) && os(iOS)
@preconcurrency import WalletCore

final class KMPRecoveryProvider: WalletCore.IdentityRecoveryProvider, @unchecked Sendable {
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
        return try await identityProviderCall { try await provider.store(recordID: recordId, data: data) }.toKMPReceipt()
    }
    func __retrieve(recordId: String) async throws -> WalletCore.IdentityRecoveryData? {
        guard let data = try await identityProviderCall({ try await provider.retrieve(recordID: recordId) }) else { return nil }
        guard (1...4096).contains(data.count) else { throw WalletError.internalFailure("Invalid recovery record size") }
        return WalletCore.IdentityRecoveryData(bytes: data.toKotlinByteArray())
    }
    func __delete(recordId: String) async throws -> WalletCore.RecoveryReceipt {
        try await identityProviderCall { try await provider.delete(recordID: recordId) }.toKMPReceipt()
    }
    private let provider: any IdentityRecoveryProvider
}
final class KMPCustodian: WalletCore.IdentityKeyCustodian, @unchecked Sendable {
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
    private let custodian: any IdentityKeyCustodian
}

extension WalletCore.RecoveryReceipt {
    func toSwiftReceipt() -> WalletRecoveryReceipt {
        switch self {
        case .acceptedLocally: return .acceptedLocally
        case .confirmedByProvider: return .confirmedByProvider
        }
    }
}

extension WalletRecoveryReceipt {
    func toKMPReceipt() -> WalletCore.RecoveryReceipt {
        switch self {
        case .acceptedLocally: return .acceptedLocally
        case .confirmedByProvider: return .confirmedByProvider
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
#endif
