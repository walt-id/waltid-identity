# Persistence and encryption

`MobileWalletConfig()` uses managed encrypted SQLDelight persistence by default on Android and iOS. Normal SDK users do not provide a database key: the SDK generates one per wallet database, stores it in platform-protected storage, and uses SQLCipher for the local wallet database.

Managed signing keys are device-local by default. They protect data at rest on the current device, but they are not a cross-device recovery mechanism. Use `MobileWalletDatabaseKey.Provided` when an app needs enterprise/KMS ownership or recoverable database-key material. Credential and DID store overrides are independent; signing keys always remain platform-managed. Supported mobile platforms intentionally do not fall back to plaintext wallet databases.

`MobileWalletConfig()` does not accept any OpenID4VP `transaction_data` profiles by default. Wallet apps must pass the profile types they understand through `presentation.transactionDataProfiles`; requests containing unknown transaction data types are rejected before the user can submit a presentation. Profile fields are preserved for app UI and display metadata.

When an app enables `urn:eudi:sca:payment:1`, reviewed ordinary and Digital
Credentials API submissions require a generated, non-exportable hardware signing
key with `BiometricCurrentSet` or `BiometricTimedReuse`. The SDK checks the restored
native key's protection and policy provenance before signing. Per-use keys authenticate
during signing. Timed keys require fresh native biometrics for each payment, followed
by a single-use signer that expires with the configured window and closes when the
operation ends. A successful authorized signature establishes possession and inherence;
the proof reports both methods as
`other`, without claiming a specific biometric modality or certified WSCD category.
Cancellation, expiry, missing native evidence, and unsupported key policies produce no
payment proof. This does not upgrade an existing key's policy.

The application remains responsible for transaction display, informed consent and
action lifetime. The payment scope enforces fresh approval and one proof; the native
key retains its timed policy for ordinary operations. This adapter does not supply
transaction UI, authentication batching across payments, or full SCA assurance.

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
        credentialStore = appCredentialStore
    )
)
```
<!-- doc-snippet:end kotlin-custom-credential-store -->

KMP consumers can override credential and DID storage while signing keys remain platform-managed:

<!-- doc-snippet:start kotlin-store-overrides -->
```kotlin
val config = MobileWalletConfig(
    walletId = "consumer-wallet",
    persistence = MobileWalletPersistence(
        credentialStore = appCredentialStore,
        didStore = appDidStore,
    )
)
```
<!-- doc-snippet:end kotlin-store-overrides -->

Call `MobileWallet.deleteWallet()` to delete local wallet material for a wallet: stored key descriptors, credentials, DIDs, platform signing keys, encrypted database files and sidecars, and the configured database key. Credential and DID custom stores receive the corresponding remove calls.

If a local development build has an old plaintext database or a database restored without its matching key, opening the wallet can fail with a typed storage error. Reset local state by calling `deleteWallet()`, uninstalling the app, or deleting the app's local wallet data. WAL-1085 does not perform plaintext-to-encrypted migration.
