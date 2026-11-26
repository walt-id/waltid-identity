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

Apps that own more wallet durability can also provide a DID store. Omitted credential and DID stores use the encrypted local database. Signing keys are always platform-managed and remain in the iOS Keychain. This example assumes `AppDidStore` implements the corresponding store protocol.

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

Call `try await wallet.deleteLocalData()` to remove local material for that wallet. The active credential and DID stores receive their remove calls; the SDK removes platform-managed signing keys, encrypted database files and sidecars, plus the configured database key. Local development databases created before encrypted persistence may fail to open; reset the app by calling `deleteLocalData()`, uninstalling the app, or deleting local app data.
