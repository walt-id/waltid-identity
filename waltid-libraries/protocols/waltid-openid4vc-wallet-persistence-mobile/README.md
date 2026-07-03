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

This library provides Android/iOS persistence implementations for the wallet store interfaces defined in [waltid-openid4vc-wallet](../waltid-openid4vc-wallet). It uses SQLDelight for credential and DID storage, SQLCipher-capable platform drivers for encrypted wallet databases, plus platform key stores for non-exportable platform-backed key references.

Use this library when you need persistent wallet storage inside a mobile wallet application.

## Features

- **SQLDelight** — Kotlin Multiplatform database layer for Android and iOS
- **Encrypted local databases** — SQLCipher-backed Android and iOS drivers
- **Platform key stores** — Android KeyStore and iOS Keychain / Secure Enclave integration
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
import id.walt.wallet2.persistence.stores.PlatformKeyStore
import id.walt.wallet2.persistence.stores.SqlDelightCredentialStore
import id.walt.wallet2.persistence.stores.SqlDelightDidStore

val databaseName = "wallet_wallet-id"
val databaseKey = AndroidDatabaseEncryptionKeyProvider(context)
    .getOrCreateKey(walletId = "wallet-id", databaseName = databaseName)
val driver = DriverFactory(context).createEncryptedDriver(
    databaseName = databaseName,
    encryptionKey = databaseKey,
)
val queries = WalletPersistenceDatabase(driver).walletPersistenceQueries

val keyProvider = AndroidPlatformKeyProvider()
val keyStore = PlatformKeyStore(keyProvider, queries)
val credentialStore = SqlDelightCredentialStore(queries)
val didStore = SqlDelightDidStore(queries)
```

The higher-level `waltid-openid4vc-wallet-mobile` facade performs this wiring automatically. Use this module directly only when assembling custom platform persistence.

## Encryption and cleanup

The mobile facade opens encrypted databases by default and keeps SDK-managed database keys in platform-protected storage. Android stores the wrapped database key with Android KeyStore-backed material and app-private preferences. iOS stores the database key as a Keychain generic-password item using device-local accessibility.

`DatabaseEncryptionKeyProvider` is the boundary for integrator-managed database keys. Implement it when an app needs KMS-backed recovery or enterprise key ownership. The SDK will request key material for opening the local SQLCipher database and call `deleteKey(walletId, databaseName)` when `MobileWallet.deleteWallet()` deletes SDK-owned local data. External KMS recovery state remains the integrator's responsibility.

`DriverFactory.deleteDatabase(databaseName)` removes the database file and SQLite sidecars used by the platform driver. For complete wallet deletion, close the driver first, remove platform signing keys referenced by `PlatformKeyStore`, delete database files, and then delete the database encryption key.

## Store Implementations

| Interface | Implementation | Description |
|-----------|----------------|-------------|
| `WalletKeyStore` | `PlatformKeyStore` | Platform-backed key references and persisted software key material |
| `WalletCredentialStore` | `SqlDelightCredentialStore` | Stored credentials with metadata |
| `WalletDidStore` | `SqlDelightDidStore` | DIDs and their documents |

## Database Schema

The mobile schema includes the following tables:

- `wallet2_keys` — Stored keys
- `wallet2_credentials` — Stored credentials
- `wallet2_dids` — Stored DIDs

Schema creation and migration are managed by SQLDelight on the target platform driver.

## API documentation

Generate the mobile persistence API reference with Dokka:

```bash
./gradlew :waltid-libraries:protocols:waltid-openid4vc-wallet-persistence-mobile:dokkaGeneratePublicationHtml -PenableAndroidBuild=true -PenableIosBuild=true
```

The generated HTML is written to `build/dokka/html`.

## Related Libraries

- **[waltid-openid4vc-wallet](../waltid-openid4vc-wallet)** — Core wallet library (store interfaces)
- **[waltid-openid4vc-wallet-mobile](../waltid-openid4vc-wallet-mobile)** — Mobile wallet facade built on these stores
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
