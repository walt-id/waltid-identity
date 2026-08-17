@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile

import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.did.dids.Crypto2DidService
import app.cash.sqldelight.db.SqlDriver
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKeyProvider
import id.walt.wallet2.persistence.keys.PlatformManagedKeyProvider
import id.walt.wallet2.persistence.stores.SqlDelightKeyStore
import id.walt.wallet2.persistence.stores.SqlDelightCredentialStore
import id.walt.wallet2.persistence.stores.SqlDelightDidStore
import id.walt.wallet2.persistence.stores.SqlDelightIssuanceSessionStore
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.waltid.openid4vci.wallet.metadata.CredentialIssuerMetadataTrustResolver
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.uuid.Uuid

/**
 * Configuration for creating a [MobileWallet].
 *
 * @property walletId Stable wallet identifier used for database naming and persisted wallet state.
 * @property defaultKeyType Key type used by [MobileWallet.bootstrap] when no key type override is supplied.
 * @property attestationConfig Optional client-attestation configuration for issuer deployments that require it.
 * @property persistence Persistence mode used for wallet-local state.
 * @property onEvent Optional callback for observing wallet issuance and presentation session events.
 * @property preferredLocales Ordered BCP 47 locale preferences used for progressive language-tag lookup.
 * When no preference matches, selection falls back to an unlocalized entry and then the first entry.
 * @property transactionDataProfiles Transaction data profiles this mobile wallet accepts in OpenID4VP requests.
 * @property credentialIssuerMetadataTrustResolver Optional trust boundary for signed Credential Issuer Metadata.
 * @property credentialRegistry Platform metadata registry. Platform factories install their native default when omitted.
 * @property readerTrustEvaluator Application trust policy for verified ISO 18013-7 reader chains.
 * @property crossProcessAccess Optional shared-container/keychain configuration for provider extensions.
 * @property onDigitalCredentialRegistryChanged Called after a credential-set mutation republishes
 * platform registration metadata. Failures do not roll back the committed wallet mutation.
 */
public data class MobileWalletConfig(
    public val walletId: String = "default",
    public val defaultKeyType: MobileWalletKeyType = MobileWalletKeyType.secp256r1,
    public val attestationConfig: WalletAttestationConfig? = null,
    public val persistence: MobileWalletPersistence = MobileWalletPersistence(),
    public val onEvent: suspend (MobileWalletEvent) -> Unit = {},
    public val preferredLocales: List<String> = emptyList(),
    public val transactionDataProfiles: List<MobileWalletTransactionDataProfile> = emptyList(),
    public val credentialIssuerMetadataTrustResolver: CredentialIssuerMetadataTrustResolver? = null,
    public val credentialRegistry: MobileWalletCredentialRegistry = UnavailableMobileWalletCredentialRegistry,
    public val readerTrustEvaluator: MobileWalletReaderTrustEvaluator = UnconfiguredMobileWalletReaderTrustEvaluator,
    public val crossProcessAccess: MobileWalletCrossProcessAccess? = null,
    public val onDigitalCredentialRegistryChanged: suspend () -> Unit = {},
)

/**
 * Cross-process wallet access required by native document-provider extensions.
 *
 * @property appGroupIdentifier Apple App Group used to share wallet state with the extension.
 * @property keychainAccessGroup Keychain access group shared by the app and extension.
 */
public data class MobileWalletCrossProcessAccess(
    public val appGroupIdentifier: String,
    public val keychainAccessGroup: String,
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
 * @property credentialStore Optional credential-store override. `null` uses the encrypted SQLDelight store.
 * @property didStore Optional DID-store override. `null` uses the encrypted SQLDelight store.
 */
public data class MobileWalletPersistence(
    public val databaseKey: MobileWalletDatabaseKey = MobileWalletDatabaseKey.Managed,
    public val credentialStore: WalletCredentialStore? = null,
    public val didStore: WalletDidStore? = null,
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
 * Platform factory that wires [MobileWallet] to Android or iOS storage and key infrastructure.
 */
public expect class MobileWalletFactory {
    /**
     * Creates a mobile wallet instance for the current platform.
     *
     * @param config Wallet configuration. Defaults use the stable `default` wallet identifier and P-256 key material.
     */
    public suspend fun create(config: MobileWalletConfig = MobileWalletConfig()): MobileWallet

    /**
     * Creates a mobile wallet with explicit verifier client-ID trust configuration.
     */
    public suspend fun create(
        config: MobileWalletConfig,
        clientIdTrustConfiguration: ClientIdTrustConfiguration,
    ): MobileWallet

}

internal suspend fun createEncryptedSqlDelightMobileWallet(
    config: MobileWalletConfig,
    clientIdTrustConfiguration: ClientIdTrustConfiguration,
    managedDatabaseKeyProvider: DatabaseEncryptionKeyProvider,
    platformKeyProvider: PlatformManagedKeyProvider,
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
        clientIdTrustConfiguration = clientIdTrustConfiguration,
        db = db,
        keyProvider = platformKeyProvider,
        deleteLocalPersistence = {
            runCatching { driver.close() }
            deleteDatabase(databaseName)
            databaseKeyProvider.deleteKey(config.walletId, databaseName)
        },
    )
}

internal fun createSqlDelightMobileWallet(
    config: MobileWalletConfig,
    clientIdTrustConfiguration: ClientIdTrustConfiguration,
    db: WalletPersistenceDatabase,
    keyProvider: PlatformManagedKeyProvider,
    didService: Crypto2DidService = Crypto2DidService,
    deleteLocalPersistence: suspend () -> Unit,
): MobileWallet {
    val queries = db.walletPersistenceQueries
    val keyStore = SqlDelightKeyStore(keyProvider, queries)
    val credentialStore = config.persistence.credentialStore ?: SqlDelightCredentialStore(queries)
    val didStore = config.persistence.didStore ?: SqlDelightDidStore(queries)
    val issuanceSessionStore = SqlDelightIssuanceSessionStore(queries)
    return MobileWallet(
        walletId = config.walletId,
        keyStore = keyStore,
        didStore = didStore,
        credentialStore = credentialStore,
        issuanceSessionStore = issuanceSessionStore,
        generateAndPersistKey = { keyType ->
            keyStore.generateManagedKey(
                id = KeyId("wallet_key_${Uuid.random()}"),
                spec = keyType.toKeySpec(),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        },
        didService = didService,
        defaultKeyType = config.defaultKeyType,
        attestationConfig = config.attestationConfig,
        preferredLocales = config.preferredLocales,
        transactionDataProfiles = config.transactionDataProfiles,
        clientIdTrustConfiguration = clientIdTrustConfiguration,
        credentialIssuerMetadataTrustResolver = config.credentialIssuerMetadataTrustResolver,
        onEvent = config.onEvent,
        credentialRegistry = config.credentialRegistry,
        onDigitalCredentialRegistryChanged = config.onDigitalCredentialRegistryChanged,
        readerTrustEvaluator = config.readerTrustEvaluator,
        deleteLocalPersistence = deleteLocalPersistence,
    )
}
