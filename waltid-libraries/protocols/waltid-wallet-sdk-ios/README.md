<div align="center">
<h1>walt.id OpenID4VC Wallet Swift Facade</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Swift facade for the walt.id mobile wallet SDK.</p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://www.linkedin.com/company/walt-id/">
<img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
</a>

  <h2>Status</h2>
  <p align="center">
    <img src="https://img.shields.io/badge/🟢%20Actively%20Maintained-success?style=for-the-badge&logo=check-circle" alt="Status: Actively Maintained" />
    <br/>
    <em>This project is being actively maintained by the development team at walt.id.<br />Regular updates, bug fixes, and new features are being added.</em>
  </p>
</div>


This package is the WAL-1065 iOS consumer boundary. It exposes Swift-native
types and a `Wallet` actor while keeping Kotlin Multiplatform, SKIE, and
generated bridge symbols behind the package implementation.

## Status

- Minimum iOS version: 15.4
- Runtime platform: iOS only
- Public API style: `async`/`await`, `Sendable` values, typed `WalletError`
- Documentation: DocC catalog in `Sources/WalletSDK/Documentation.docc`
- Local binary dependency: `WalletCore.xcframework`
- Current delivery mode: local package plus locally assembled XCFramework

## Local Build

Generate the KMP core XCFramework before building an iOS consumer:

```bash
./gradlew :waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:assembleWalletCoreReleaseXCFramework -PenableIosBuild=true
```

The Swift package expects the local artifact at:

```text
../waltid-openid4vc-wallet-mobile/build/XCFrameworks/release/WalletCore.xcframework
```

Then build or test the Swift facade:

```bash
swift test --package-path waltid-libraries/protocols/waltid-wallet-sdk-ios -Xswiftc -strict-concurrency=complete -Xswiftc -warnings-as-errors
```

Generate and validate the Swift DocC archive:

```bash
waltid-libraries/protocols/waltid-wallet-sdk-ios/scripts/generate-docc.sh
```

The validation keeps owned public Swift symbols covered by DocC abstracts and
parameter documentation. The mobile docs workflow also checks that persistence
examples in README and DocC pages match the compiled Kotlin and Swift snippet
tests:

```bash
python3 waltid-libraries/protocols/waltid-wallet-sdk-ios/scripts/check-mobile-wallet-doc-snippets.py
```

## Usage Sketch

```swift
import WalletSDK

let wallet = try await Wallet(
    configuration: WalletConfiguration(walletID: "consumer-wallet")
)

let bootstrap = try await wallet.bootstrap(didMethod: "key")
let resolution = try await wallet.resolveOffer(offer: credentialOfferURL)
let transactionCode = resolution.transactionCodeRequired
    ? userEnteredTransactionCode
    : nil
let credentialIDs = try await wallet.receive(
    offer: credentialOfferURL,
    txCode: transactionCode
)
let credentials = try await wallet.credentials()
let presentation = try await wallet.present(
    request: authorizationRequestURL,
    did: bootstrap.did
)
```

## Local persistence

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
            stores: WalletStores(credentials: AppCredentialStore())
        )
    )
)
```
<!-- doc-snippet:end swift-custom-credential-store -->

Apps that own more wallet durability can also provide DID and signing-key stores. Omitted credential and DID stores use the encrypted local database, while an omitted key store uses platform signing-key persistence and generation. Signing-key overrides are configured through `WalletKeys` so the app-owned `WalletKeyStore` and generator are supplied atomically. This example assumes `AppDidStore` and `AppKeyStore` implement the corresponding store protocols, and that `AppKeyStore` exposes app-owned key generation.

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

`StoredKey.serializedKeyJSON` is a walt.id serialized key payload and may contain private signing material. Treat it like a secret and store it only in app-owned secure storage.

Provided database keys and custom stores can be combined when an app owns both database-key recovery and wallet-record durability:

<!-- doc-snippet:start swift-combined-persistence -->
```swift
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
```
<!-- doc-snippet:end swift-combined-persistence -->

Call `try await wallet.deleteLocalData()` to remove local material for that wallet. The active credential, DID, and key stores receive their remove calls; the SDK then removes encrypted database files and sidecars plus the configured database key. Local development databases created before encrypted persistence may fail to open; reset the app by calling `deleteLocalData()`, uninstalling the app, or deleting local app data.

## Native iOS Consumer

The native iOS consumer proof lives in the existing demo app:

```text
waltid-applications/waltid-wallet-demo-ios
```

That app imports `WalletSDK` directly from SwiftUI and exercises the same
Swift package boundary a native iOS integrator would use. The separate Compose
Multiplatform demo remains the KMP/Compose consumer proof and uses its generated
Kotlin `sharedUI` framework instead of routing through this Swift facade.

## Interop Boundary

`WalletSDK` is the intended public iOS API. It exposes Swift-owned models,
errors, and the `Wallet` actor so app code does not need to import or handle
generated Kotlin, Objective-C, or SKIE symbols directly.

`WalletCore` is the local binary implementation dependency behind that facade.
It is assembled from `waltid-openid4vc-wallet-mobile` with SKIE enabled for the
KMP features the facade consumes: cancellable Swift `async` calls for Kotlin
`suspend` functions, Flow-to-`AsyncSequence` support for wallet events, and
Swift-friendly enum/sealed wrappers at the bridge boundary.

The facade is currently kept as a separate SwiftPM source target instead of
bundling the hand-written Swift wrappers into `WalletCore`. That keeps the
public SDK module small for DocC, symbol graph checks, and host-side SwiftPM
tests. SKIE Swift code bundling remains a viable future packaging option if the
release shape should become a single binary framework that includes both the
KMP core and the Swift facade.

The Compose Multiplatform demo intentionally does not apply this Swift facade.
Its iOS app hosts Kotlin Compose UI through the generated `sharedUI` framework,
which is the right proof path for KMP/Compose integrators. SKIE would become
useful there only if the demo adds a native SwiftUI path that directly consumes
shared Kotlin state, flows, sealed UI state, or suspend APIs.

## Publishing Follow-up

This package currently uses a local binary target path for the spike. A customer
preview or release should switch the binary target to a URL plus checksum after
the team chooses the private artifact host and release CI workflow.

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>