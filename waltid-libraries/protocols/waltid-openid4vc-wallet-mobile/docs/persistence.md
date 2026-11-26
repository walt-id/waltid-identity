# Persistence and encryption

`WalletConfiguration()` uses managed encrypted SQLDelight persistence by default on Android and iOS. Normal SDK users do not provide a database key: the SDK generates one per wallet database, stores it in platform-protected storage, and uses SQLCipher for the local wallet database.

The database key protects the local database at rest. Use `WalletDatabaseKey.Provided` when the app manages or recovers that key. Credential and DID store overrides are independent of the signing identity. Its `storage` records whether the signing key is hardware-backed, uses native storage or is held in the encrypted database.

`WalletConfiguration()` does not accept any OpenID4VP `transaction_data` profiles by default. Wallet apps must pass the profile types they understand through `presentation.transactionDataProfiles`; requests containing unknown transaction data types are rejected before the user can submit a presentation. Profile fields are preserved for app UI and display metadata.

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

The examples below build `WalletConfiguration` values. Pass the selected config
to `WalletFactory(...).open(config)` from a coroutine to create the
wallet.

Use the encrypted default when the app does not need custom persistence:

<!-- doc-snippet:start kotlin-default-persistence -->
```kotlin
val config = WalletConfiguration(walletId = "consumer-wallet")
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

val config = WalletConfiguration(
    walletId = "consumer-wallet",
    persistence = WalletPersistence(
        databaseKey = WalletDatabaseKey.Provided(
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
val config = WalletConfiguration(
    walletId = "consumer-wallet",
    persistence = WalletPersistence(
        credentialStore = appCredentialStore
    )
)
```
<!-- doc-snippet:end kotlin-custom-credential-store -->

KMP apps can supply credential and DID stores together. The signing identity retains its selected key storage.

<!-- doc-snippet:start kotlin-store-overrides -->
```kotlin
val config = WalletConfiguration(
    walletId = "consumer-wallet",
    persistence = WalletPersistence(
        credentialStore = appCredentialStore,
        didStore = appDidStore,
    )
)
```
<!-- doc-snippet:end kotlin-store-overrides -->

Call `Wallet.deleteLocalData()` to delete local wallet material for a wallet: stored key descriptors, credentials, DIDs, platform signing keys, encrypted database files and sidecars, and the configured database key. Credential and DID custom stores receive the corresponding remove calls.

Close every in-process wallet using the database, its managed key or a shared credential or DID store before erasing it. If an app and extension share storage, the host must coordinate both processes before erasure.

If opening fails, restore the matching database key and encrypted database from the host’s recovery source. If recovery is unavailable and the user chooses to discard the wallet, use the host’s reset procedure for the database, key provider and custom stores. A disposable Android development installation can clear app storage. The SDK does not migrate plaintext databases.
