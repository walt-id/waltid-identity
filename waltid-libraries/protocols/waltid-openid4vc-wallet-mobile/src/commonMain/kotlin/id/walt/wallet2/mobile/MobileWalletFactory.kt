package id.walt.wallet2.mobile

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
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
    val persistence: MobileWalletPersistenceConfig = MobileWalletPersistenceConfig.SdkManagedEncrypted(),
    val onEvent: suspend (MobileWalletEvent) -> Unit = {},
)

/**
 * Backup and recovery ownership for SDK-managed mobile wallet persistence.
 */
enum class BackupPolicy {
    /**
     * Keep SDK-managed database keys bound to the current app install/device.
     */
    DeviceLocalOnly,

    /**
     * The integrator owns database-key recovery outside the SDK.
     */
    IntegratorManagedRecovery,
}

/**
 * Selects how [MobileWalletFactory] wires wallet-local persistence.
 */
sealed interface MobileWalletPersistenceConfig {

    /**
     * Uses encrypted SQLDelight persistence and SDK-managed database keys.
     *
     * @property backupPolicy Backup and recovery ownership for the generated database key.
     */
    data class SdkManagedEncrypted(
        val backupPolicy: BackupPolicy = BackupPolicy.DeviceLocalOnly,
    ) : MobileWalletPersistenceConfig

    /**
     * Uses encrypted SQLDelight persistence with database keys supplied by the integrator.
     *
     * @property keyProvider Provider that returns the SQLCipher key material for this wallet database.
     * @property backupPolicy Backup and recovery ownership for the supplied database key.
     */
    data class IntegratorManagedKey(
        val keyProvider: DatabaseEncryptionKeyProvider,
        val backupPolicy: BackupPolicy = BackupPolicy.IntegratorManagedRecovery,
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
     * @param config Wallet configuration. Defaults create a new wallet identifier and P-256 key material.
     */
    fun create(config: MobileWalletConfig = MobileWalletConfig()): MobileWallet
}

internal fun createMobileWallet(
    config: MobileWalletConfig,
    db: WalletPersistenceDatabase,
    keyProvider: PlatformKeyProvider,
    deleteLocalPersistence: suspend () -> Unit = {},
): MobileWallet = createMobileWallet(config) {
    createSqlDelightMobileWallet(config, db, keyProvider, deleteLocalPersistence)
}

internal fun createMobileWallet(
    config: MobileWalletConfig,
    createSqlDelightWallet: () -> MobileWallet,
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
