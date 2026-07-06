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

``WalletDatabaseKey`` descriptions redact raw key material, but apps should still
avoid logging, serializing, or otherwise exposing the `material` bytes.

Use ``WalletStores`` with ``WalletCredentialStore`` to override credential
storage while keeping the managed encrypted database key, DID store, and
platform signing-key store.

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
- ``WalletAttestationConfiguration``

### Wallet Data

- ``Credential``
- ``StoredCredential``
- ``WalletBootstrapResult``
- ``PresentationResult``

### Events

- ``WalletEvent``
- ``WalletEventPhase``
- ``WalletEventStatus``

### Errors And Key Types

- ``WalletError``
- ``WalletKeyType``
