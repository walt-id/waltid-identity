#if !os(iOS) || WALLET_SDK_APP_HOST_TESTS
import Foundation
import XCTest
@testable import WalletSDK

final class WalletPersistenceSnippetsTests: XCTestCase {
    // The examples share an app wallet ID but demonstrate incompatible database-key owners.
    // Remove each example's database and key before the next one opens that same wallet.
    private func deleteAfterTest(_ wallet: Wallet) {
        #if os(iOS)
        addTeardownBlock { try await wallet.deleteLocalData() }
        #endif
    }

    func testProvidedDatabaseKeySnippetCompiles() async throws {
        // doc-snippet:start swift-provided-database-key
        struct KMSDatabaseKeyProvider: DatabaseEncryptionKeyProvider {
            func getOrCreateKey(walletID: String, databaseName: String) async throws -> DatabaseEncryptionKey {
                let keyData = try await loadOrCreateKeyData(walletID: walletID, databaseName: databaseName)
                return DatabaseEncryptionKey(keyID: "\(walletID):\(databaseName)", material: keyData)
            }

            func deleteKey(walletID: String, databaseName: String) async throws {
                try await deleteKeyData(walletID: walletID, databaseName: databaseName)
            }
        }

        let wallet = try await Wallet(
            configuration: WalletConfiguration(
                walletID: "consumer-wallet",
                persistence: WalletPersistence(
                    databaseKeySource: .provided(KMSDatabaseKeyProvider())
                )
            )
        )
        // doc-snippet:end swift-provided-database-key

        deleteAfterTest(wallet)
        let configuration = await wallet.configuration
        XCTAssertTrue(configuration.persistence.databaseKeySource.isProvided)
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
                    credentialStore: AppCredentialStore()
                )
            )
        )
        // doc-snippet:end swift-custom-credential-store

        deleteAfterTest(wallet)
        let configuration = await wallet.configuration
        XCTAssertNotNil(configuration.persistence.credentialStore)
    }

    func testCredentialAndDidStoreOverridesSnippetCompiles() async throws {
        // doc-snippet:start swift-credential-and-did-overrides
        let wallet = try await Wallet(
            configuration: WalletConfiguration(
                walletID: "consumer-wallet",
                persistence: WalletPersistence(
                    credentialStore: AppCredentialStore(),
                    didStore: AppDidStore()
                )
            )
        )
        // doc-snippet:end swift-credential-and-did-overrides

        deleteAfterTest(wallet)
        let configuration = await wallet.configuration
        XCTAssertNotNil(configuration.persistence.credentialStore)
        XCTAssertNotNil(configuration.persistence.didStore)
    }

    func testCombinedProvidedDatabaseKeyAndOverridesSnippetCompiles() async throws {
        // doc-snippet:start swift-combined-persistence
        let wallet = try await Wallet(
            configuration: WalletConfiguration(
                walletID: "consumer-wallet",
                persistence: WalletPersistence(
                    databaseKeySource: .provided(KMSDatabaseKeyProvider()),
                    credentialStore: AppCredentialStore(),
                    didStore: AppDidStore()
                )
            )
        )
        // doc-snippet:end swift-combined-persistence

        deleteAfterTest(wallet)
        let configuration = await wallet.configuration
        XCTAssertTrue(configuration.persistence.databaseKeySource.isProvided)
        XCTAssertNotNil(configuration.persistence.credentialStore)
        XCTAssertNotNil(configuration.persistence.didStore)
    }
}

private struct KMSDatabaseKeyProvider: DatabaseEncryptionKeyProvider {
    func getOrCreateKey(walletID: String, databaseName: String) async throws -> DatabaseEncryptionKey {
        let keyData = try await loadOrCreateKeyData(walletID: walletID, databaseName: databaseName)
        return DatabaseEncryptionKey(keyID: "\(walletID):\(databaseName)", material: keyData)
    }

    func deleteKey(walletID: String, databaseName: String) async throws {
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

private func loadOrCreateKeyData(walletID: String, databaseName: String) async throws -> Data {
    precondition(!walletID.isEmpty)
    precondition(!databaseName.isEmpty)
    return Data(repeating: 7, count: 32)
}

private func deleteKeyData(walletID: String, databaseName: String) async throws {
    precondition(!walletID.isEmpty)
    precondition(!databaseName.isEmpty)
}

private extension DatabaseKeySource {
    var isProvided: Bool {
        switch self {
        case .managed:
            return false
        case .provided:
            return true
        }
    }
}

#endif
