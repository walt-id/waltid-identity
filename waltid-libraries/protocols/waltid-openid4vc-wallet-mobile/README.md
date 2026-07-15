<div align="center">
<h1>walt.id OpenID4VC Wallet Mobile</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Mobile facade for Android and iOS wallet SDK integrations.</p>

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

Mobile facade for the walt.id wallet SDK. This module exposes the Android/iOS-facing wallet API used by the native demo apps while delegating protocol behavior to [waltid-openid4vc-wallet](../waltid-openid4vc-wallet/README.md) and persistence to [waltid-openid4vc-wallet-persistence-mobile](../waltid-openid4vc-wallet-persistence-mobile/README.md).

Native iOS apps should consume the Swift [WalletSDK](../waltid-wallet-sdk-ios/README.md) package, which wraps this KMP module behind Swift-owned types, `async`/`await`, and DocC documentation.

For local setup and platform build flags, see the [Mobile Wallet Development Guide](../../../docs/mobile-wallet-development.md).

## Capabilities

- Bootstrap a mobile wallet with platform-backed keys and DID material.
- Receive credentials using OpenID4VCI.
- List credentials stored in mobile persistence.
- Present credentials using OpenID4VP.
- Support mobile issuance flows using OAuth 2.0 client attestation.

## Persistence and encryption

`MobileWalletConfig()` uses managed encrypted SQLDelight persistence by default on Android and iOS. Normal SDK users do not provide a database key: the SDK generates one per wallet database, stores it in platform-protected storage, and uses SQLCipher for the local wallet database.

Managed keys are device-local by default. They protect data at rest on the current device, but they are not a cross-device recovery mechanism. Use `MobileWalletDatabaseKey.Provided` when an app needs enterprise/KMS ownership or recoverable database-key material. Store overrides are independent: `null` credential and DID overrides use the encrypted SQLDelight database opened by this persistence configuration, while a `null` key override keeps platform-backed signing-key persistence and generation. Supported mobile platforms intentionally do not fall back to plaintext wallet databases.

`MobileWalletConfig()` does not accept any OpenID4VP `transaction_data` profiles by default. Wallet apps must pass the profile types they understand through `transactionDataProfiles`; requests containing unknown transaction data types are rejected before the user can submit a presentation. Profile fields are preserved for app UI and display metadata.

The examples below build `MobileWalletConfig` values. Pass the selected config
to `MobileWalletFactory(...).create(config)` from a coroutine to create the
wallet.

Use the encrypted default when the app does not need custom persistence:

<!-- doc-snippet:start kotlin-default-persistence -->
```kotlin
val config = MobileWalletConfig(walletId = "consumer-wallet")
```
<!-- doc-snippet:end kotlin-default-persistence -->

Provide database keys while keeping SDK SQLDelight stores by implementing `DatabaseEncryptionKeyProvider`:

<!-- doc-snippet:start kotlin-provided-database-key -->
```kotlin
class KmsDatabaseKeyProvider : DatabaseEncryptionKeyProvider {
    override suspend fun getOrCreateKey(walletId: String, databaseName: String): DatabaseEncryptionKey {
        val keyBytes = loadOrCreateKeyBytes(walletId, databaseName)
        return DatabaseEncryptionKey(keyId = "$walletId:$databaseName", material = keyBytes)
    }

    override suspend fun deleteKey(walletId: String, databaseName: String) {
        deleteKeyBytes(walletId, databaseName)
    }
}

val config = MobileWalletConfig(
    walletId = "consumer-wallet",
    persistence = MobileWalletPersistence(
        databaseKey = MobileWalletDatabaseKey.Provided(
            provider = KmsDatabaseKeyProvider()
        )
    )
)
```
<!-- doc-snippet:end kotlin-provided-database-key -->

This mode is covered by Android device and iOS simulator integration tests so
provider lookup, encrypted database reopening, and provider deletion stay wired
to the real platform drivers.

Override only credential storage while retaining the default encrypted database, database-key ownership, DID store, and platform signing-key store:

<!-- doc-snippet:start kotlin-custom-credential-store -->
```kotlin
val config = MobileWalletConfig(
    walletId = "consumer-wallet",
    persistence = MobileWalletPersistence(
        stores = MobileWalletStores(
            credentials = appCredentialStore
        )
    )
)
```
<!-- doc-snippet:end kotlin-custom-credential-store -->

KMP consumers can override all wallet stores. Key storage and key generation are configured together so platform-managed signing keys cannot be accidentally mixed with app-owned key persistence:

<!-- doc-snippet:start kotlin-full-store-overrides -->
```kotlin
val config = MobileWalletConfig(
    walletId = "consumer-wallet",
    persistence = MobileWalletPersistence(
        stores = MobileWalletStores(
            credentials = appCredentialStore,
            dids = appDidStore,
            keys = MobileWalletKeys(
                store = appKeyStore,
                generate = { keyType -> appKeyProvider.generateKey(keyType) }
            )
        )
    )
)
```
<!-- doc-snippet:end kotlin-full-store-overrides -->

Call `MobileWallet.deleteWallet()` to delete local wallet material for a wallet: stored key references, credentials, DIDs, platform signing keys referenced by the active key store, encrypted database files and sidecars, and the configured database key. Store cleanup uses the active store interfaces, so custom stores receive the same remove calls as default stores.

If a local development build has an old plaintext database or a database restored without its matching key, opening the wallet can fail with a typed storage error. Reset local state by calling `deleteWallet()`, uninstalling the app, or deleting the app's local wallet data. WAL-1085 does not perform plaintext-to-encrypted migration.

## Demo apps

- [Compose Wallet Demo](../../../waltid-applications/waltid-wallet-demo-compose/README.md)
- [iOS Wallet Demo](../../../waltid-applications/waltid-wallet-demo-ios/README.md)

## API documentation

Generate the SDK facade API reference with Dokka:

```bash
./gradlew :waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:dokkaGeneratePublicationHtml -PenableAndroidBuild=true -PenableIosBuild=true
```

The generated HTML is written to `build/dokka/html`.

The native Swift iOS facade has a separate DocC catalog in [WalletSDK](../waltid-wallet-sdk-ios/README.md).
