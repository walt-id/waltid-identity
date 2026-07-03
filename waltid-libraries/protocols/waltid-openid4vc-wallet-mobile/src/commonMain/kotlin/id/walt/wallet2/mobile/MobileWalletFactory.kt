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
public data class MobileWalletConfig(
    val walletId: String = "default",
    val defaultKeyType: MobileWalletKeyType = MobileWalletKeyType.secp256r1,
    val attestationConfig: WalletAttestationConfig? = null,
    val persistence: MobileWalletPersistenceConfig = MobileWalletPersistenceConfig.SdkManagedEncrypted(),
    val onEvent: suspend (MobileWalletEvent) -> Unit = {},
)

enum class BackupPolicy {
    DeviceLocalOnly,
    IntegratorManagedRecovery,
}

sealed interface MobileWalletPersistenceConfig {

    data class SdkManagedEncrypted(
        val backupPolicy: BackupPolicy = BackupPolicy.DeviceLocalOnly,
    ) : MobileWalletPersistenceConfig

    data class IntegratorManagedKey(
        val keyProvider: DatabaseEncryptionKeyProvider,
        val backupPolicy: BackupPolicy = BackupPolicy.IntegratorManagedRecovery,
    ) : MobileWalletPersistenceConfig

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
public expect class MobileWalletFactory {
    /**
     * Creates a mobile wallet instance for the current platform.
     *
     * @param config Wallet configuration. Defaults create a new wallet identifier and P-256 key material.
     */
    public fun create(config: MobileWalletConfig = MobileWalletConfig()): MobileWallet
}

internal fun createMobileWallet(
    config: MobileWalletConfig,
    db: WalletPersistenceDatabase,
    keyProvider: PlatformKeyProvider,
): MobileWallet = createMobileWallet(config) {
    createSqlDelightMobileWallet(config, db, keyProvider)
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
    )
}
