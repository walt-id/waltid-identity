<div align="center">
<h1>walt.id Wallet SDK - Mobile Persistence Layer</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>SQLDelight-backed mobile persistence for the walt.id Wallet SDK</p>

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

## Overview

This library provides Android/iOS persistence implementations for the wallet store interfaces defined in [waltid-openid4vc-wallet](../waltid-openid4vc-wallet). It uses SQLDelight for credential and DID storage, SQLCipher-capable platform drivers for encrypted wallet databases, and managed or software signing-key descriptors.

Use this library when you need persistent wallet storage inside a mobile wallet application.

## Features

- **SQLDelight** — Kotlin Multiplatform database layer for Android and iOS
- **Encrypted local databases** — SQLCipher-backed Android and iOS drivers
- **Platform key stores** — Android KeyStore and iOS Keychain / Secure Enclave integration for managed keys
- **Credential persistence** — SQL-backed credential storage with metadata
- **DID persistence** — SQL-backed DID document storage
- **Shared schema** — Common mobile schema across supported platforms

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
repositories {
    maven("https://maven.waltid.dev/releases")
}

dependencies {
    implementation("id.walt.protocols:waltid-openid4vc-wallet-persistence-mobile:<version>")
}
```

## Usage

### Constructing Stores

Inside a coroutine:

```kotlin
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.encryption.AndroidDatabaseEncryptionKeyProvider
import id.walt.wallet2.persistence.keys.AndroidPlatformKeyProvider
import id.walt.wallet2.persistence.stores.DriverFactory
import id.walt.wallet2.persistence.stores.SqlDelightKeyStore
import id.walt.wallet2.persistence.stores.SqlDelightCredentialStore
import id.walt.wallet2.persistence.stores.SqlDelightDidStore

val walletId = "wallet-id"
val databaseName = "wallet_$walletId"
val databaseKey = AndroidDatabaseEncryptionKeyProvider(context)
    .getOrCreateKey(walletId, databaseName)
val driver = DriverFactory(context).createEncryptedDriver(
    databaseName = databaseName,
    encryptionKey = databaseKey,
    walletId = walletId,
)
val queries = WalletPersistenceDatabase(driver).walletPersistenceQueries

val keyProvider = AndroidPlatformKeyProvider(context, interactionContextProvider = { currentActivity })
val keyStore = SqlDelightKeyStore(keyProvider, queries, identityWalletId = walletId)
val credentialStore = SqlDelightCredentialStore(queries)
val didStore = SqlDelightDidStore(queries, identityWalletId = walletId)
```

The higher-level `waltid-openid4vc-wallet-mobile` facade performs this wiring automatically. Use this module directly only when assembling custom platform persistence.

`SqlDelightKeyStore` persists one versioned Crypto2 `StoredKey` descriptor per
key. The SQL schema remains `key_id`, `created_at`, and `stored_key`; key type,
backing, authorization policy, and native alias metadata are authoritative in
the descriptor rather than duplicated SQL columns. The store checks the native provider before generation. It can fall back to a
`StoredKey.Software` descriptor only with default protection and platform
settings, no key-use authorization and no native attestation challenge. Missing ordinary managed keys return `null`; missing protected keys surface
`KeyUseAuthorizationFailure.ProtectedKeyUnavailable`.

The host-configurable authorization prompt is captured when a protected key is
created and remains associated with that persisted Signum policy. The stored policy applies when the key is restored.

## Encryption and cleanup

Even when an app supplies both a credential store and a DID store, opening the wallet still opens SQLCipher for signing-identity state and pending issuance. The SDK keeps managed database keys in platform-protected storage. Android stores the wrapped database key with Android KeyStore-backed material and app-private preferences. iOS stores the database key as a Keychain generic-password item using device-local accessibility.

`DatabaseEncryptionKeyProvider` is the boundary for provided database keys. Implement it when an app needs KMS-backed recovery or enterprise key ownership. The SDK will request key material for opening the local SQLCipher database and call `deleteKey(walletId, databaseName)` when `Wallet.deleteLocalData()` deletes local wallet data. External KMS recovery state remains the app's responsibility.

`DriverFactory.deleteDatabase(databaseName)` removes the database file and SQLite sidecars used by the platform driver. Use `Wallet.deleteLocalData()` for complete wallet deletion. It clears configured stores and referenced signing keys while the database is open, closes the driver, removes the database files, and then asks the provider to delete the database key.

## Store Implementations

| Interface | Implementation | Description |
|-----------|----------------|-------------|
| `WalletKeyStore` | `SqlDelightKeyStore` | Versioned managed or software key descriptors |
| `WalletCredentialStore` | `SqlDelightCredentialStore` | Stored credentials with metadata |
| `WalletDidStore` | `SqlDelightDidStore` | DIDs and their documents |
| `AtomicWalletIssuanceSessionCreationStore` | `SqlDelightIssuanceSessionStore` | Pending issuance records and atomic claims |

`SqlDelightStatusMaintenanceStore` manages background status grants and scan progress. Its sessions use the existing encrypted wallet database.

## Database Schema

The [SQLDelight schema](src/commonMain/sqldelight/id/walt/wallet2/persistence/db/WalletPersistence.sq) contains key references, credentials, DIDs, credential-status snapshots and maintenance state, issuance sessions, and the signing-identity journal and active binding. The foreground driver applies the versioned SQLDelight migrations when opening the database.

## API documentation

Generate the mobile persistence API reference with Dokka:

```bash
./gradlew :waltid-libraries:protocols:waltid-openid4vc-wallet-persistence-mobile:dokkaGeneratePublicationHtml -PenableAndroidBuild=true -PenableIosBuild=true
```

The generated HTML is written to `build/dokka/html`.

## Related Libraries

- **[waltid-openid4vc-wallet](../waltid-openid4vc-wallet)** — Core wallet library (store interfaces)
- **[waltid-openid4vc-wallet-mobile](../waltid-openid4vc-wallet-mobile)** — Wallet SDK built on these stores
- **[waltid-openid4vc-wallet-persistence-server](../waltid-openid4vc-wallet-persistence-server)** — Server-side Exposed persistence

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more in-depth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>
