import Foundation
#if canImport(WalletCore) && os(iOS)
@preconcurrency import WalletCore

extension WalletPersistence {
    func toKMPPersistence() -> WalletBridgePersistence {
        WalletBridgePersistence(
            databaseKey: databaseKey.toKMPDatabaseKeyConfiguration(),
            credentialStore: credentialStore.map { KMPWalletCredentialStoreAdapter(store: $0) },
            didStore: didStore.map { KMPWalletDidStoreAdapter(store: $0) }
        )
    }

    func toKMPDatabaseKeyProvider() -> WalletBridgeDatabaseEncryptionKeyProvider? {
        databaseKey.toKMPDatabaseKeyProvider()
    }
}

extension WalletDatabaseKeyConfiguration {
    func toKMPDatabaseKeyConfiguration() -> WalletBridgeDatabaseKeyConfiguration {
        switch self {
        case .managed:
            return .managed
        case .provided:
            return .provided
        }
    }

    func toKMPDatabaseKeyProvider() -> WalletBridgeDatabaseEncryptionKeyProvider? {
        switch self {
        case .managed:
            return nil
        case let .provided(provider):
            return KMPWalletDatabaseKeyProviderAdapter(provider: provider)
        }
    }
}

private final class KMPWalletDatabaseKeyProviderAdapter: WalletBridgeDatabaseEncryptionKeyProvider, @unchecked Sendable {
    init(provider: any WalletDatabaseKeyProvider) {
        self.provider = provider
    }

    func __getOrCreateKey(walletId: String, databaseName: String) async throws -> WalletBridgeDatabaseEncryptionKey {
        let key = try await provider.databaseKey(walletID: walletId, databaseName: databaseName)
        return WalletBridgeDatabaseEncryptionKey(
            keyId: key.keyID,
            material: key.material.toKotlinByteArray()
        )
    }

    func __deleteKey(walletId: String, databaseName: String) async throws {
        try await provider.deleteDatabaseKey(walletID: walletId, databaseName: databaseName)
    }

    private let provider: any WalletDatabaseKeyProvider
}

private final class KMPWalletCredentialStoreAdapter: WalletBridgeCredentialStore, WalletBridgeStoreIdentity, @unchecked Sendable {
    var storageOwner: Any { hostStoreOwner(store) }

    init(store: any WalletCredentialStore) {
        self.store = store
    }

    func __getCredential(id: String) async throws -> WalletBridgeStoredCredential? {
        try await store.credential(id: id)?.toKMPStoredCredential()
    }

    func __listCredentials() async throws -> [WalletBridgeStoredCredential] {
        try await store.credentials().map { $0.toKMPStoredCredential() }
    }

    func __addCredential(entry: WalletBridgeStoredCredential) async throws {
        try await store.addCredential(entry.toSwiftStoredCredential())
    }

    func __removeCredential(id: String) async throws -> KotlinBoolean {
        KotlinBoolean(bool: try await store.removeCredential(id: id))
    }

    private let store: any WalletCredentialStore
}

private final class KMPWalletDidStoreAdapter: WalletBridgeDidStore, WalletBridgeStoreIdentity, @unchecked Sendable {
    var storageOwner: Any { hostStoreOwner(store) }

    init(store: any WalletDidStore) {
        self.store = store
    }

    func __getDid(did: String) async throws -> WalletBridgeStoredDid? {
        try await store.did(id: did)?.toKMPStoredDid()
    }

    func __listDids() async throws -> [WalletBridgeStoredDid] {
        try await store.dids().map { $0.toKMPStoredDid() }
    }

    func __addDid(entry: WalletBridgeStoredDid) async throws {
        try await store.addDid(entry.toSwiftStoredDid())
    }

    func __removeDid(did: String) async throws -> KotlinBoolean {
        KotlinBoolean(bool: try await store.removeDid(id: did))
    }

    private let store: any WalletDidStore
}

private extension StoredCredential {
    func toKMPStoredCredential() -> WalletBridgeStoredCredential {
        WalletBridgeStoredCredential(
            id: id,
            serializedCredential: serializedCredential,
            format: format,
            label: label,
            addedAt: addedAt.map { ISO8601DateFormatter().string(from: $0) },
            metadataJson: metadataJSON
        )
    }
}

private extension WalletBridgeStoredCredential {
    func toSwiftStoredCredential() -> StoredCredential {
        StoredCredential(
            id: id,
            serializedCredential: serializedCredential,
            format: format,
            label: label,
            addedAt: addedAt.flatMap(parseWalletISO8601Date),
            metadataJSON: metadataJson
        )
    }
}

private extension StoredDid {
    func toKMPStoredDid() -> WalletBridgeStoredDid {
        WalletBridgeStoredDid(
            did: did,
            documentJson: documentJSON
        )
    }
}

private extension WalletBridgeStoredDid {
    func toSwiftStoredDid() -> StoredDid {
        StoredDid(
            did: did,
            documentJSON: documentJson
        )
    }
}
// Swift value stores can share a backing resource without exposing reference identity.
private final class ValueStoreOwner: @unchecked Sendable {
    static let shared = ValueStoreOwner()
}

private func hostStoreOwner(_ store: Any) -> AnyObject {
    if type(of: store) is AnyClass { return store as AnyObject }
    return ValueStoreOwner.shared
}
#endif
