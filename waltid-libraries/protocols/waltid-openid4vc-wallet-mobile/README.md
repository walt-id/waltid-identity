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

`MobileWalletConfig()` uses SDK-managed encrypted SQLDelight persistence by default on Android and iOS. Normal SDK users do not provide a database key: the SDK generates one per wallet database, stores it in platform-protected storage, and uses SQLCipher for the local wallet database.

SDK-managed keys are device-local by default. They protect data at rest on the current device, but they are not a cross-device recovery mechanism. Use `MobileWalletPersistenceConfig.IntegratorManagedKey` when an app needs enterprise/KMS ownership or recoverable database-key material, and use `MobileWalletPersistenceConfig.CustomStores` to replace SDK persistence entirely. Supported mobile platforms intentionally do not fall back to plaintext wallet databases.

Create a wallet with the encrypted default from a coroutine:

```kotlin
val wallet = MobileWalletFactory(context).create(
    MobileWalletConfig(walletId = "consumer-wallet")
)
```

Provide database keys while keeping SDK SQLDelight stores by implementing `DatabaseEncryptionKeyProvider`:

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

val wallet = MobileWalletFactory(context).create(
    MobileWalletConfig(
        walletId = "consumer-wallet",
        persistence = MobileWalletPersistenceConfig.IntegratorManagedKey(
            keyProvider = KmsDatabaseKeyProvider()
        )
    )
)
```

This mode is covered by Android device and iOS simulator integration tests so
provider lookup, encrypted database reopening, and provider deletion stay wired
to the real platform drivers.

Replace SDK SQLDelight persistence entirely with custom stores when the app owns durability, transactions, encryption, backup, migration, and deletion:

```kotlin
val wallet = MobileWalletFactory(context).create(
    MobileWalletConfig(
        walletId = "consumer-wallet",
        persistence = MobileWalletPersistenceConfig.CustomStores(
            keyStore = appKeyStore,
            didStore = appDidStore,
            credentialStore = appCredentialStore,
            keyGenerator = { keyType -> appKeyProvider.generateKey(keyType) }
        )
    )
)
```

`CustomStores` bypasses platform SQLDelight persistence, Android Keystore, and
iOS Keychain database-key storage. Its behavior is platform-independent and is
covered in common KMP tests, including store injection and `deleteWallet()`
cleanup through the injected store interfaces.

Call `MobileWallet.deleteWallet()` to delete SDK-owned local wallet material for a wallet: stored key references, credentials, DIDs, platform signing keys referenced by the wallet, encrypted database files and sidecars, and the SDK-managed database key. For `CustomStores`, cleanup remains owned by the integrator; the facade only calls the store-level remove operations exposed by the injected stores.

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
