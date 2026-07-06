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
public data class MobileWalletConfig(
    val walletId: String = "default",
    val defaultKeyType: MobileWalletKeyType = MobileWalletKeyType.secp256r1,
    val attestationConfig: WalletAttestationConfig? = null,
    val persistence: MobileWalletPersistence = MobileWalletPersistence(),
    val onEvent: suspend (MobileWalletEvent) -> Unit = {},
)

/**
 * Wallet-local persistence configuration.
 *
 * @property databaseKey Owner of the SQLCipher key used for the encrypted local wallet database.
 * @property stores Optional store overrides. `null` entries keep the platform default for that state category.
 */
data class MobileWalletPersistence(
    val databaseKey: MobileWalletDatabaseKey = MobileWalletDatabaseKey.Managed,
    val stores: MobileWalletStores = MobileWalletStores(),
)

/**
 * Selects who owns the encrypted wallet database key.
 */
sealed interface MobileWalletDatabaseKey {
    /**
     * Uses platform-protected storage to create, load, and delete the database key.
     */
    data object Managed : MobileWalletDatabaseKey

    /**
     * Uses key material supplied by application code.
     *
     * @property provider Provider that returns SQLCipher key material for this wallet database.
     */
    data class Provided(
        val provider: DatabaseEncryptionKeyProvider,
    ) : MobileWalletDatabaseKey
}

/**
 * Optional store overrides for wallet-local state.
 *
 * `null` entries keep the platform default for that state category: credentials and DID
 * documents use the encrypted SQLDelight database, while signing keys use platform-backed
 * key persistence and generation.
 *
 * @property credentials Optional credential store override.
 * @property dids Optional DID document store override.
 * @property keys Optional atomic signing-key store and generation override.
 */
data class MobileWalletStores(
    val credentials: WalletCredentialStore? = null,
    val dids: WalletDidStore? = null,
    val keys: MobileWalletKeys? = null,
)

/**
 * Atomic override for wallet signing-key persistence and generation.
 *
 * Key storage and key generation are configured together because platform default signing keys
 * are coupled to their platform-protected key stores.
 *
 * @property store Store for wallet signing-key references.
 * @property generate Generator used when the wallet needs to create signing keys.
 */
data class MobileWalletKeys(
    val store: WalletKeyStore,
    val generate: suspend (KeyType) -> Key,
)

/**
 * Platform factory that wires [MobileWallet] to Android or iOS storage and key infrastructure.
 */
public expect class MobileWalletFactory {
    /**
     * Creates a mobile wallet instance for the current platform.
     *
     * @param config Wallet configuration. Defaults use the stable `default` wallet identifier and P-256 key material.
     */
    public suspend fun create(config: MobileWalletConfig = MobileWalletConfig()): MobileWallet
}

internal suspend fun createEncryptedSqlDelightMobileWallet(
    config: MobileWalletConfig,
    managedDatabaseKeyProvider: DatabaseEncryptionKeyProvider,
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
    val databaseKeyProvider = when (val databaseKey = config.persistence.databaseKey) {
        is MobileWalletDatabaseKey.Managed -> managedDatabaseKeyProvider
        is MobileWalletDatabaseKey.Provided -> databaseKey.provider
    }
    val driver = openEncryptedDriver(
        databaseName,
        databaseKeyProvider.getOrCreateKey(config.walletId, databaseName),
        config.persistence.databaseKey is MobileWalletDatabaseKey.Managed,
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
    val keyOverride = config.persistence.stores.keys
    val keyStore = keyOverride?.store ?: PlatformKeyStore(keyProvider, queries)
    val credentialStore = config.persistence.stores.credentials ?: SqlDelightCredentialStore(queries)
    val didStore = config.persistence.stores.dids ?: SqlDelightDidStore(queries)
    val keyGenerator = keyOverride?.generate ?: { keyType: KeyType -> keyProvider.generateKey(keyType) }

    return MobileWallet(
        walletId = config.walletId,
        keyStore = keyStore,
        didStore = didStore,
        credentialStore = credentialStore,
        keyGenerator = keyGenerator,
        defaultKeyType = config.defaultKeyType,
        attestationConfig = config.attestationConfig,
        onEvent = config.onEvent,
        deleteLocalPersistence = deleteLocalPersistence,
    )
}
