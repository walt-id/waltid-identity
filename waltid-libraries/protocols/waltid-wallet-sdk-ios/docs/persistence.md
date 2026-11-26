# Local persistence

`WalletConfiguration()` uses managed persistence by default. The SDK opens an encrypted SQLDelight database through SQLCipher and manages the per-wallet database key internally in iOS Keychain. Apps using the normal Swift facade do not pass database key material.

The managed key is device-local. It protects local wallet data at rest, but it is not a cross-device recovery mechanism. Apps that need enterprise/KMS ownership or recoverable database-key material can provide an app-owned key provider:

<!-- doc-snippet:start swift-provided-database-key -->
```swift
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
```
<!-- doc-snippet:end swift-provided-database-key -->

The provider shape is compiled by the SwiftPM test suite, and the underlying
KMP bridge is covered by iOS simulator integration tests so provider lookup,
encrypted database reopening, and provider deletion stay wired to the real iOS
driver.

Apps can override credential storage while retaining the default encrypted database, database-key ownership, DID store, and platform signing-key store:

<!-- doc-snippet:start swift-custom-credential-store -->
```swift
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
```
<!-- doc-snippet:end swift-custom-credential-store -->

Apps that own more wallet durability can also provide a DID store. Omitted credential and DID stores use the encrypted local database. The signing identity’s `storage` records whether its key uses hardware, native storage or the encrypted database. This example assumes `AppDidStore` implements the corresponding store protocol.

<!-- doc-snippet:start swift-full-store-overrides -->
```swift
let wallet = try await Wallet(
    configuration: WalletConfiguration(
        walletID: "consumer-wallet",
        persistence: WalletPersistence(
            credentialStore: AppCredentialStore(),
            didStore: AppDidStore()
        )
    )
)
```
<!-- doc-snippet:end swift-full-store-overrides -->

Provided database keys and custom stores can be combined when an app owns both database-key recovery and wallet-record durability:

<!-- doc-snippet:start swift-combined-persistence -->
```swift
let wallet = try await Wallet(
    configuration: WalletConfiguration(
        walletID: "consumer-wallet",
        persistence: WalletPersistence(
            databaseKey: .provided(KMSDatabaseKeyProvider()),
            credentialStore: AppCredentialStore(),
            didStore: AppDidStore()
        )
    )
)
```
<!-- doc-snippet:end swift-combined-persistence -->

Call `try await wallet.deleteLocalData()` on an opened wallet to erase its managed signing keys, database files, sidecars and database key. The active credential and DID stores receive the corresponding removal requests. Close other wallets using the same storage and coordinate app and extension processes before erasure.
Swift class and actor stores retain their identity across wallet opens. Close other wallets using custom Swift value-type stores before erasure, because the wallet cannot identify storage shared behind those values. Hosts coordinating separate store objects that access the same backing service must also coordinate erasure.

If opening fails, restore the matching key and encrypted database using the host’s recovery mechanism. If the user chooses to discard the wallet, use the host’s reset procedure for its app-group container, Keychain entries and custom stores. An app reinstall can leave Keychain items or shared-container data in place.
