package id.walt.wallet2.mobile

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeyUseAuthorizationException
import id.walt.crypto.keys.KeyUseAuthorizationFailure
import id.walt.crypto.keys.KeyUseAuthorizationPolicy
import id.walt.crypto.keys.KeyUseAuthorizationPrompt
import app.cash.sqldelight.db.SqlDriver
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKeyProvider
import id.walt.wallet2.persistence.keys.PlatformKeyProvider
import id.walt.wallet2.persistence.keys.PlatformKeyCapability
import id.walt.wallet2.persistence.keys.PlatformKeyGenerationRequest
import id.walt.wallet2.persistence.stores.PlatformKeyStore
import id.walt.wallet2.persistence.stores.SqlDelightCredentialStore
import id.walt.wallet2.persistence.stores.SqlDelightDidStore
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry

/**
 * Configuration for creating a [MobileWallet].
 *
 * @property walletId Stable wallet identifier used for database naming and persisted wallet state.
 * @property defaultKeyType Key type used by [MobileWallet.bootstrap] when no key type override is supplied.
 * @property defaultKeyUseAuthorizationPolicy Authorization policy applied only when creating new keys.
 * @property keyUseAuthorizationPrompt Localized operating-system prompt text used for protected signing.
 * @property attestationConfig Optional client-attestation configuration for issuer deployments that require it.
 * @property persistence Persistence mode used for wallet-local state.
 * @property onEvent Optional callback for observing wallet issuance and presentation session events.
 * @property transactionDataProfiles Transaction data profiles this mobile wallet accepts in OpenID4VP requests.
 */
public data class MobileWalletConfig(
    public val walletId: String = "default",
    public val defaultKeyType: MobileWalletKeyType = MobileWalletKeyType.secp256r1,
    public val attestationConfig: WalletAttestationConfig? = null,
    public val persistence: MobileWalletPersistence = MobileWalletPersistence(),
    public val onEvent: suspend (MobileWalletEvent) -> Unit = {},
    public val transactionDataProfiles: List<MobileWalletTransactionDataProfile> = emptyList(),
    public val defaultKeyUseAuthorizationPolicy: KeyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.None,
    public val keyUseAuthorizationPrompt: KeyUseAuthorizationPrompt = KeyUseAuthorizationPrompt(),
)

/**
 * Transaction data profile accepted by the mobile wallet.
 *
 * Wallet apps should keep this list aligned with the ecosystem or service they trust.
 * Requests containing transaction data with a type outside this list are rejected before
 * the user can submit a presentation.
 *
 * @property type Collision-resistant OpenID4VP `transaction_data.type` value.
 * @property displayName Human-readable label for consent UI.
 * @property fields Supported transaction type-specific fields.
 */
public data class MobileWalletTransactionDataProfile(
    public val type: String,
    public val displayName: String = type,
    public val fields: List<String> = emptyList(),
)

internal fun List<MobileWalletTransactionDataProfile>.toTransactionDataTypeRegistry(): TransactionDataTypeRegistry =
    TransactionDataTypeRegistry(map { it.type }.toSet())

/**
 * Wallet-local persistence configuration.
 *
 * @property databaseKey Owner of the SQLCipher key used for the encrypted local wallet database.
 * @property stores Optional store overrides. `null` entries keep the platform default for that state category.
 */
public data class MobileWalletPersistence(
    public val databaseKey: MobileWalletDatabaseKey = MobileWalletDatabaseKey.Managed,
    public val stores: MobileWalletStores = MobileWalletStores(),
)

/**
 * Selects who owns the encrypted wallet database key.
 */
public sealed interface MobileWalletDatabaseKey {
    /**
     * Uses platform-protected storage to create, load, and delete the database key.
     */
    public data object Managed : MobileWalletDatabaseKey

    /**
     * Uses key material supplied by application code.
     *
     * @property provider Provider that returns SQLCipher key material for this wallet database.
     */
    public data class Provided(
        public val provider: DatabaseEncryptionKeyProvider,
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
public data class MobileWalletStores(
    public val credentials: WalletCredentialStore? = null,
    public val dids: WalletDidStore? = null,
    public val keys: MobileWalletKeys? = null,
)

/**
 * Atomic override for wallet signing-key persistence and generation.
 *
 * Key storage and key generation are configured together because platform default signing keys
 * are coupled to their platform-protected key stores.
 *
 * @property store Store for wallet signing-key references.
 * @property generate Legacy generator used only for requests with [KeyUseAuthorizationPolicy.None].
 * @property generateForRequest Generator that explicitly receives key type, identifier, and authorization policy.
 * @property capability Optional preflight implementation for custom protected-key generators.
 */
public data class MobileWalletKeys(
    public val store: WalletKeyStore,
    public val generate: suspend (KeyType) -> Key,
    public val generateForRequest: (suspend (PlatformKeyGenerationRequest) -> Key)? = null,
    public val capability: (suspend (KeyType, KeyUseAuthorizationPolicy) -> PlatformKeyCapability)? = null,
) {
    internal suspend fun generate(request: PlatformKeyGenerationRequest): Key =
        generateForRequest?.invoke(request) ?: if (
            request.keyUseAuthorizationPolicy == KeyUseAuthorizationPolicy.None
        ) {
            generate(request.keyType)
        } else {
            throw KeyUseAuthorizationException(
                failure = KeyUseAuthorizationFailure.UnsupportedCombination,
                message = "The legacy custom key generator does not support protected key requests",
            )
        }

    internal suspend fun capabilityFor(
        keyType: KeyType,
        policy: KeyUseAuthorizationPolicy,
    ): PlatformKeyCapability = capability?.invoke(keyType, policy)
        ?: PlatformKeyProvider.defaultCustomCapability(keyType, policy)
}

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
    val keyGenerator: suspend (PlatformKeyGenerationRequest) -> Key = keyOverride?.let { override ->
        { request -> override.generate(request) }
    } ?: keyProvider::generateKey
    val keyCapability: suspend (KeyType, KeyUseAuthorizationPolicy) -> PlatformKeyCapability =
        keyOverride?.let { override ->
            { keyType, policy -> override.capabilityFor(keyType, policy) }
        } ?: keyProvider::capability

    return MobileWallet(
        walletId = config.walletId,
        keyStore = keyStore,
        didStore = didStore,
        credentialStore = credentialStore,
        keyGenerator = keyGenerator,
        keyCapability = keyCapability,
        defaultKeyType = config.defaultKeyType,
        defaultKeyUseAuthorizationPolicy = config.defaultKeyUseAuthorizationPolicy,
        attestationConfig = config.attestationConfig,
        transactionDataProfiles = config.transactionDataProfiles,
        onEvent = config.onEvent,
        deleteLocalPersistence = deleteLocalPersistence,
    )
}

private fun PlatformKeyProvider.Companion.defaultCustomCapability(
    keyType: KeyType,
    policy: KeyUseAuthorizationPolicy,
): PlatformKeyCapability = PlatformKeyCapability(
    platform = id.walt.wallet2.persistence.keys.PlatformKeyPlatform.Custom,
    keyType = keyType,
    keyUseAuthorizationPolicy = policy,
    supported = policy == KeyUseAuthorizationPolicy.None,
    platformBackingAvailable = false,
    secureHardwareRequired = false,
    secureHardwareAvailable = null,
    failure = if (policy == KeyUseAuthorizationPolicy.None) null else KeyUseAuthorizationFailure.UnsupportedCombination,
)
