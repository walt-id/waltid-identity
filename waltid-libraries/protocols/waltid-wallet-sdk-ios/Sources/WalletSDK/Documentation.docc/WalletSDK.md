# ``WalletSDK``

Swift-friendly iOS facade for the walt.id mobile wallet SDK.

## Overview

`WalletSDK` gives iOS apps a native Swift entry point for wallet setup,
OpenID4VCI credential issuance, local credential listing, OpenID4VP
presentation, and progress events.

Create a ``Wallet`` in an app process, bootstrap wallet key material, and
then call the async wallet operations with Foundation-native `URL` values:

```swift
let wallet = try await Wallet(
    configuration: WalletConfiguration(walletID: "consumer-wallet")
)

let bootstrap = try await wallet.bootstrap(didMethod: "key")
let credentialIDs = try await wallet.receive(offer: credentialOfferURL)
let credentials = try await wallet.credentials()
let presentation = try await wallet.present(
    request: authorizationRequestURL,
    did: bootstrap.did
)
```

The public API intentionally exposes Swift actors, structs, enums, and typed
errors. Kotlin Multiplatform and bridge implementation details stay behind this
module boundary.

### Local Persistence

``WalletConfiguration`` defaults to managed encrypted persistence. The SDK
opens a SQLCipher-encrypted local wallet database and stores the per-wallet
database key in iOS Keychain. Normal Swift SDK users do not provide database
key material.

Use ``WalletPersistence`` with ``WalletDatabaseKeyConfiguration/provided(_:)`` when an app
needs enterprise/KMS ownership or recoverable database-key material:

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

``WalletDatabaseKey`` descriptions redact raw key material, but apps should still
avoid logging, serializing, or otherwise exposing the `material` bytes.

Use ``WalletStores`` when an app owns credential, DID, or signing-key
durability. Store overrides are independent except for signing keys:
``WalletKeys`` keeps the ``WalletKeyStore`` and its generator together so newly
generated keys are persisted into the same app-owned key domain. This example
assumes app-defined store types that implement the corresponding protocols.

<!-- doc-snippet:start swift-full-store-overrides -->
```swift
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
```
<!-- doc-snippet:end swift-full-store-overrides -->

Provided database keys and custom stores can be combined when an app owns both
database-key recovery and wallet-record durability. ``StoredKey`` carries
walt.id serialized key JSON and may contain private signing material, so apps
should keep it in app-owned secure storage and avoid logging it.

Use ``Wallet/deleteLocalData()`` to reset local data for the wallet.
This removes wallet records, platform signing keys referenced by the wallet,
encrypted database files and sidecars, and managed database keys. When using
a provided database-key provider, deletion also calls
``WalletDatabaseKeyProvider/deleteDatabaseKey(walletID:databaseName:)``. Old
local development databases created before encrypted persistence may need this
reset; plaintext-to-encrypted migration is not performed.

## Topics

### Integration Guides

- <doc:GettingStarted>
- <doc:IssuingCredentials>
- <doc:PresentingCredentials>
- <doc:ObservingEventsAndErrors>

### Wallet

- ``Wallet``
- ``WalletConfiguration``
- ``WalletPersistence``
- ``WalletDatabaseKeyConfiguration``
- ``WalletDatabaseKeyProvider``
- ``WalletDatabaseKey``
- ``WalletStores``
- ``WalletCredentialStore``
- ``WalletDidStore``
- ``WalletKeyStore``
- ``WalletKeys``
- ``WalletAttestationConfiguration``

### Wallet Data

- ``Credential``
- ``StoredCredential``
- ``StoredDid``
- ``StoredKey``
- ``WalletKeyInfo``
- ``WalletBootstrapResult``
- ``PresentationResult``

### Events

- ``WalletEvent``
- ``WalletEventPhase``
- ``WalletEventStatus``

### Errors And Key Types

- ``WalletError``
- ``WalletKeyType``
