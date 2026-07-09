import Foundation
import XCTest
@testable import WalletSDK

final class WalletPersistenceSnippetsTests: XCTestCase {
    func testProvidedDatabaseKeySnippetCompiles() async throws {
        // doc-snippet:start swift-provided-database-key
        struct KMSDatabaseKeyProvider: WalletDatabaseKeyProvider {
            func databaseKey(walletID: String, databaseName: String) async throws -> WalletDatabaseKey {
                let keyData = try await loadOrCreateKeyData(walletID: walletID, databaseName: databaseName)
                return WalletDatabaseKey(keyID: "\(walletID):\(databaseName)", material: keyData)
            }

            func deleteDatabaseKey(walletID: String, databaseName: String) async throws {
                try await deleteKeyData(walletID: walletID, databaseName: databaseName)
            }
        }

        let wallet = try await Wallet(
            configuration: WalletConfiguration(
                walletID: "consumer-wallet",
                persistence: WalletPersistence(
                    databaseKey: .provided(KMSDatabaseKeyProvider())
                )
            )
        )
        // doc-snippet:end swift-provided-database-key

        let configuration = await wallet.configuration
        XCTAssertTrue(configuration.persistence.databaseKey.isProvided)
    }

    func testCustomCredentialStoreSnippetCompiles() async throws {
        // doc-snippet:start swift-custom-credential-store
        actor AppCredentialStore: WalletCredentialStore {
            private var entries: [String: StoredCredential] = [:]

            func credential(id: String) async throws -> StoredCredential? {
                entries[id]
            }

            func credentials() async throws -> [StoredCredential] {
                Array(entries.values)
            }

            func addCredential(_ credential: StoredCredential) async throws {
                entries[credential.id] = credential
            }

            func removeCredential(id: String) async throws -> Bool {
                entries.removeValue(forKey: id) != nil
            }
        }

        let wallet = try await Wallet(
            configuration: WalletConfiguration(
                walletID: "consumer-wallet",
                persistence: WalletPersistence(
                    stores: WalletStores(credentials: AppCredentialStore())
                )
            )
        )
        // doc-snippet:end swift-custom-credential-store

        let configuration = await wallet.configuration
        XCTAssertNotNil(configuration.persistence.stores.credentials)
    }

    func testFullStoreOverridesSnippetCompiles() async throws {
        // doc-snippet:start swift-full-store-overrides
        let keyStore = AppKeyStore()

        let wallet = try await Wallet(
            configuration: WalletConfiguration(
                walletID: "consumer-wallet",
                persistence: WalletPersistence(
                    stores: WalletStores(
                        credentials: AppCredentialStore(),
                        dids: AppDidStore(),
                        keys: WalletKeys(store: keyStore) { keyType in
                            try await keyStore.generateKey(type: keyType)
                        }
                    )
                )
            )
        )
        // doc-snippet:end swift-full-store-overrides

        let configuration = await wallet.configuration
        XCTAssertNotNil(configuration.persistence.stores.credentials)
        XCTAssertNotNil(configuration.persistence.stores.dids)
        XCTAssertNotNil(configuration.persistence.stores.keys)
    }

    func testCombinedProvidedDatabaseKeyAndStoresSnippetCompiles() async throws {
        // doc-snippet:start swift-combined-persistence
        let keyStore = AppKeyStore()

        let wallet = try await Wallet(
            configuration: WalletConfiguration(
                walletID: "consumer-wallet",
                persistence: WalletPersistence(
                    databaseKey: .provided(KMSDatabaseKeyProvider()),
                    stores: WalletStores(
                        credentials: AppCredentialStore(),
                        dids: AppDidStore(),
                        keys: WalletKeys(store: keyStore) { keyType in
                            try await keyStore.generateKey(type: keyType)
                        }
                    )
                )
            )
        )
        // doc-snippet:end swift-combined-persistence

        let configuration = await wallet.configuration
        XCTAssertTrue(configuration.persistence.databaseKey.isProvided)
        XCTAssertNotNil(configuration.persistence.stores.credentials)
        XCTAssertNotNil(configuration.persistence.stores.dids)
        XCTAssertNotNil(configuration.persistence.stores.keys)
    }
}

private struct KMSDatabaseKeyProvider: WalletDatabaseKeyProvider {
    func databaseKey(walletID: String, databaseName: String) async throws -> WalletDatabaseKey {
        let keyData = try await loadOrCreateKeyData(walletID: walletID, databaseName: databaseName)
        return WalletDatabaseKey(keyID: "\(walletID):\(databaseName)", material: keyData)
    }

    func deleteDatabaseKey(walletID: String, databaseName: String) async throws {
        try await deleteKeyData(walletID: walletID, databaseName: databaseName)
    }
}

private actor AppCredentialStore: WalletCredentialStore {
    private var entries: [String: StoredCredential] = [:]

    func credential(id: String) async throws -> StoredCredential? {
        entries[id]
    }

    func credentials() async throws -> [StoredCredential] {
        Array(entries.values)
    }

    func addCredential(_ credential: StoredCredential) async throws {
        entries[credential.id] = credential
    }

    func removeCredential(id: String) async throws -> Bool {
        entries.removeValue(forKey: id) != nil
    }
}

private actor AppDidStore: WalletDidStore {
    private var entries: [String: StoredDid] = [:]

    func did(id: String) async throws -> StoredDid? {
        entries[id]
    }

    func dids() async throws -> [StoredDid] {
        Array(entries.values)
    }

    func addDid(_ did: StoredDid) async throws {
        entries[did.id] = did
    }

    func removeDid(id: String) async throws -> Bool {
        entries.removeValue(forKey: id) != nil
    }
}

private actor AppKeyStore: WalletKeyStore {
    private var entries: [String: StoredKey] = [:]

    func key(id: String) async throws -> StoredKey? {
        entries[id]
    }

    func keys() async throws -> [WalletKeyInfo] {
        entries.values.map {
            WalletKeyInfo(keyID: $0.keyID, keyType: $0.keyType, algorithm: $0.algorithm)
        }
    }

    func addKey(_ key: StoredKey) async throws -> String {
        entries[key.id] = key
        return key.keyID
    }

    func removeKey(id: String) async throws -> Bool {
        entries.removeValue(forKey: id) != nil
    }

    func generateKey(type: WalletKeyType) async throws -> StoredKey {
        try await createSerializedWalletKey(type: type)
    }
}

private func loadOrCreateKeyData(walletID: String, databaseName: String) async throws -> Data {
    precondition(!walletID.isEmpty)
    precondition(!databaseName.isEmpty)
    return Data(repeating: 7, count: 32)
}

private func deleteKeyData(walletID: String, databaseName: String) async throws {
    precondition(!walletID.isEmpty)
    precondition(!databaseName.isEmpty)
}

private func createSerializedWalletKey(type: WalletKeyType) async throws -> StoredKey {
    StoredKey(
        keyID: "generated-\(type)",
        keyType: type,
        algorithm: nil,
        serializedKeyJSON: #"{"type":"jwk","jwk":{"kid":"generated"}}"#
    )
}

private extension WalletDatabaseKeyConfiguration {
    var isProvided: Bool {
        switch self {
        case .managed:
            return false
        case .provided:
            return true
        }
    }
}
