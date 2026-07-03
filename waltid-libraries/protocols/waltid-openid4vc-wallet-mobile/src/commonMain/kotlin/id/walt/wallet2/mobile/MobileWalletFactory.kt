package id.walt.wallet2.mobile

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import app.cash.sqldelight.db.SqlDriver
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKeyProvider
import id.walt.wallet2.persistence.keys.PlatformKeyProvider
import id.walt.wallet2.persistence.stores.PlatformKeyStore
import id.walt.wallet2.persistence.stores.SqlDelightCredentialStore
import id.walt.wallet2.persistence.stores.SqlDelightDidStore

/**
 * Configuration for creating a [MobileWallet].
 *
 * @property walletId Stable wallet identifier used for database naming and persisted wallet state.
 * @property defaultKeyType Key type used by [MobileWallet.bootstrap] when no key type override is supplied.
 * @property attestationConfig Optional client-attestation configuration for issuer deployments that require it.
 * @property persistence Persistence mode used for wallet-local state.
 * @property onEvent Optional callback for observing wallet issuance and presentation session events.
 */
data class MobileWalletConfig(
    val walletId: String = "default",
    val defaultKeyType: MobileWalletKeyType = MobileWalletKeyType.secp256r1,
    val attestationConfig: WalletAttestationConfig? = null,
    val persistence: MobileWalletPersistenceConfig = MobileWalletPersistenceConfig.SdkManagedEncrypted,
    val onEvent: suspend (MobileWalletEvent) -> Unit = {},
)

/**
 * Selects how [MobileWalletFactory] wires wallet-local persistence.
 */
sealed interface MobileWalletPersistenceConfig {

    /**
     * Uses encrypted SQLDelight persistence and SDK-managed database keys.
     */
    data object SdkManagedEncrypted : MobileWalletPersistenceConfig

    /**
     * Uses encrypted SQLDelight persistence with database keys supplied by the integrator.
     *
     * @property keyProvider Provider that returns the SQLCipher key material for this wallet database.
     */
    data class IntegratorManagedKey(
        val keyProvider: DatabaseEncryptionKeyProvider,
    ) : MobileWalletPersistenceConfig

    /**
     * Replaces SDK SQLDelight persistence with integrator-owned wallet stores.
     *
     * @property keyStore Store for wallet key references.
     * @property didStore Store for DID documents.
     * @property credentialStore Store for credentials.
     * @property keyGenerator Generator used when the wallet needs to create signing keys.
     */
    data class CustomStores(
        val keyStore: WalletKeyStore,
        val didStore: WalletDidStore,
        val credentialStore: WalletCredentialStore,
        val keyGenerator: suspend (KeyType) -> Key,
    ) : MobileWalletPersistenceConfig
}

/**
 * Platform factory that wires [MobileWallet] to Android or iOS storage and key infrastructure.
 */
expect class MobileWalletFactory {
    /**
     * Creates a mobile wallet instance for the current platform.
     *
     * @param config Wallet configuration. Defaults use the stable `default` wallet identifier and P-256 key material.
     */
    suspend fun create(config: MobileWalletConfig = MobileWalletConfig()): MobileWallet
}

internal suspend fun createMobileWallet(
    config: MobileWalletConfig,
    createSqlDelightWallet: suspend () -> MobileWallet,
): MobileWallet = when (val persistence = config.persistence) {
    is MobileWalletPersistenceConfig.CustomStores -> MobileWallet(
        walletId = config.walletId,
        keyStore = persistence.keyStore,
        didStore = persistence.didStore,
        credentialStore = persistence.credentialStore,
        keyGenerator = persistence.keyGenerator,
        defaultKeyType = config.defaultKeyType,
        attestationConfig = config.attestationConfig,
        onEvent = config.onEvent,
    )

    is MobileWalletPersistenceConfig.IntegratorManagedKey,
    is MobileWalletPersistenceConfig.SdkManagedEncrypted,
    -> createSqlDelightWallet()
}

internal suspend fun createEncryptedSqlDelightMobileWallet(
    config: MobileWalletConfig,
    sdkManagedKeyProvider: DatabaseEncryptionKeyProvider,
    platformKeyProvider: PlatformKeyProvider,
    openEncryptedDriver: (
        databaseName: String,
        encryptionKey: DatabaseEncryptionKey,
        isDeviceLocal: Boolean,
        walletId: String,
    ) -> SqlDriver,
    deleteDatabase: (databaseName: String) -> Unit,
): MobileWallet {
    val databaseName = "wallet_${config.walletId}"
    val databaseKeyProvider = when (val persistence = config.persistence) {
        is MobileWalletPersistenceConfig.SdkManagedEncrypted -> sdkManagedKeyProvider
        is MobileWalletPersistenceConfig.IntegratorManagedKey -> persistence.keyProvider
        is MobileWalletPersistenceConfig.CustomStores ->
            error("Custom store wallets do not use SDK SQLDelight persistence")
    }
    val driver = openEncryptedDriver(
        databaseName,
        databaseKeyProvider.getOrCreateKey(config.walletId, databaseName),
        config.persistence is MobileWalletPersistenceConfig.SdkManagedEncrypted,
        config.walletId,
    )
    val db = WalletPersistenceDatabase(driver)

    return createSqlDelightMobileWallet(
        config = config,
        db = db,
        keyProvider = platformKeyProvider,
        deleteLocalPersistence = {
            runCatching { driver.close() }
            deleteDatabase(databaseName)
            databaseKeyProvider.deleteKey(config.walletId, databaseName)
        },
    )
}

private fun createSqlDelightMobileWallet(
    config: MobileWalletConfig,
    db: WalletPersistenceDatabase,
    keyProvider: PlatformKeyProvider,
    deleteLocalPersistence: suspend () -> Unit,
): MobileWallet {
    val queries = db.walletPersistenceQueries
    val keyStore = PlatformKeyStore(keyProvider, queries)
    val credentialStore = SqlDelightCredentialStore(queries)
    val didStore = SqlDelightDidStore(queries)

    return MobileWallet(
        walletId = config.walletId,
        keyStore = keyStore,
        didStore = didStore,
        credentialStore = credentialStore,
        keyGenerator = { keyType -> keyProvider.generateKey(keyType) },
        defaultKeyType = config.defaultKeyType,
        attestationConfig = config.attestationConfig,
        onEvent = config.onEvent,
        deleteLocalPersistence = deleteLocalPersistence,
    )
}
