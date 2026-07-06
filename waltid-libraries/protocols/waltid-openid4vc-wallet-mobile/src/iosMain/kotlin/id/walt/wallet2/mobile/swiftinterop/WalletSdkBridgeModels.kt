package id.walt.wallet2.mobile.swiftinterop

import id.walt.credentials.CredentialParser
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletDatabaseKey
import id.walt.wallet2.mobile.MobileWalletKeyType
import id.walt.wallet2.mobile.MobileWalletPersistence
import id.walt.wallet2.mobile.MobileWalletStores
import id.walt.wallet2.mobile.WalletAttestationConfig
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKeyProvider
import id.walt.wallet2.persistence.encryption.WalletPersistenceException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Configuration used when creating an iOS [WalletSdkBridge].
 *
 * @property walletId Stable wallet identifier used for database naming and persisted wallet state.
 * @property defaultKeyType Key type used by wallet bootstrap when no key type override is supplied.
 * @property persistence Wallet-local persistence configuration.
 * @property databaseKeyProvider Swift-owned database key provider used when [persistence] uses
 * [WalletBridgeDatabaseKeyConfiguration.Provided].
 * @property attestation Optional client-attestation configuration for issuers that require it.
 */
data class WalletBridgeConfiguration(
    val walletId: String = "default",
    val defaultKeyType: MobileWalletKeyType = MobileWalletKeyType.secp256r1,
    val persistence: WalletBridgePersistence = WalletBridgePersistence(),
    val databaseKeyProvider: WalletBridgeDatabaseEncryptionKeyProvider? = null,
    val attestation: WalletAttestationConfig? = null,
)

internal fun WalletBridgeConfiguration.toMobileWalletConfig() = MobileWalletConfig(
    walletId = walletId,
    defaultKeyType = defaultKeyType,
    attestationConfig = attestation,
    persistence = persistence.toMobileWalletPersistence(databaseKeyProvider),
)

/**
 * Persistence configuration exposed to the Swift wallet bridge.
 *
 * @property databaseKey Owner of the encrypted local database key.
 * @property stores Optional store overrides exposed by the Swift facade.
 */
data class WalletBridgePersistence(
    val databaseKey: WalletBridgeDatabaseKeyConfiguration = WalletBridgeDatabaseKeyConfiguration.Managed,
    val stores: WalletBridgeStores = WalletBridgeStores(),
)

/**
 * Database-key ownership modes exposed to the Swift wallet bridge.
 */
enum class WalletBridgeDatabaseKeyConfiguration {
    /** Platform-managed encrypted database key. */
    Managed,

    /** Encrypted database key supplied by Swift app code. */
    Provided,
}

/**
 * Optional Swift-provided store overrides exposed to the bridge.
 *
 * @property credentials Optional Swift-provided credential store.
 */
data class WalletBridgeStores(
    val credentials: WalletBridgeCredentialStore? = null,
)

/**
 * Database key material returned by a Swift-owned database key provider.
 *
 * @property keyId Stable identifier for the database key.
 * @property material Raw SQLCipher key material.
 */
data class WalletBridgeDatabaseEncryptionKey(
    val keyId: String,
    val material: ByteArray,
)

/**
 * Swift-facing provider for app-supplied encrypted wallet database keys.
 */
interface WalletBridgeDatabaseEncryptionKeyProvider {
    /**
     * Returns the existing encryption key for [databaseName] or creates one if this provider owns creation.
     */
    suspend fun getOrCreateKey(walletId: String, databaseName: String): WalletBridgeDatabaseEncryptionKey

    /**
     * Deletes provider-owned key material for [databaseName], if present.
     */
    suspend fun deleteKey(walletId: String, databaseName: String)
}

/**
 * Credential entry exchanged with Swift custom credential stores.
 *
 * @property id Stable wallet-local credential identifier.
 * @property serializedCredential Raw serialized credential value.
 * @property format Credential format, for example `vc+sd-jwt` or `jwt_vc_json`.
 * @property label Optional user-facing credential label.
 * @property addedAt Optional ISO-8601 timestamp for when the credential was added.
 */
data class WalletBridgeStoredCredential(
    val id: String,
    val serializedCredential: String,
    val format: String,
    val label: String? = null,
    val addedAt: String? = null,
)

/**
 * Swift-facing credential store override.
 */
interface WalletBridgeCredentialStore {
    /**
     * Returns a stored credential by wallet-local identifier.
     */
    suspend fun getCredential(id: String): WalletBridgeStoredCredential?

    /**
     * Lists all credentials in this store.
     */
    suspend fun listCredentials(): List<WalletBridgeStoredCredential>

    /**
     * Adds or replaces a credential entry.
     */
    suspend fun addCredential(entry: WalletBridgeStoredCredential)

    /**
     * Removes a credential by wallet-local identifier.
     *
     * @return `true` when a credential existed and was removed.
     */
    suspend fun removeCredential(id: String): Boolean
}

private fun WalletBridgePersistence.toMobileWalletPersistence(
    databaseKeyProvider: WalletBridgeDatabaseEncryptionKeyProvider?,
): MobileWalletPersistence = MobileWalletPersistence(
    databaseKey = databaseKey.toMobileWalletDatabaseKey(databaseKeyProvider),
    stores = MobileWalletStores(
        credentials = stores.credentials?.let(::BridgeCredentialStore),
    ),
)

private fun WalletBridgeDatabaseKeyConfiguration.toMobileWalletDatabaseKey(
    databaseKeyProvider: WalletBridgeDatabaseEncryptionKeyProvider?,
): MobileWalletDatabaseKey = when (this) {
    WalletBridgeDatabaseKeyConfiguration.Managed ->
        MobileWalletDatabaseKey.Managed

    WalletBridgeDatabaseKeyConfiguration.Provided ->
        MobileWalletDatabaseKey.Provided(
            provider = BridgeDatabaseEncryptionKeyProvider(
                databaseKeyProvider
                    ?: throw IllegalArgumentException("Provided database-key persistence requires a database key provider"),
            )
        )
}

private class BridgeDatabaseEncryptionKeyProvider(
    private val bridgeProvider: WalletBridgeDatabaseEncryptionKeyProvider,
) : DatabaseEncryptionKeyProvider {
    override suspend fun getOrCreateKey(walletId: String, databaseName: String): DatabaseEncryptionKey {
        val key = bridgeProvider.getOrCreateKey(walletId, databaseName)
        return DatabaseEncryptionKey(
            keyId = key.keyId,
            material = key.material,
        )
    }

    override suspend fun deleteKey(walletId: String, databaseName: String) {
        bridgeProvider.deleteKey(walletId, databaseName)
    }
}

private class BridgeCredentialStore(
    private val bridgeStore: WalletBridgeCredentialStore,
) : WalletCredentialStore {
    override suspend fun getCredential(id: String): StoredCredential? =
        bridgeStore.getCredential(id)?.toStoredCredential()

    override suspend fun listCredentials(): Flow<StoredCredential> =
        bridgeStore.listCredentials().map { it.toStoredCredential() }.asFlow()

    override suspend fun addCredential(entry: StoredCredential) {
        bridgeStore.addCredential(entry.toBridgeStoredCredential())
    }

    override suspend fun removeCredential(id: String): Boolean =
        bridgeStore.removeCredential(id)
}

private suspend fun WalletBridgeStoredCredential.toStoredCredential(): StoredCredential {
    val (_, credential) = CredentialParser.detectAndParse(serializedCredential)
    return StoredCredential(
        id = id,
        credential = credential,
        label = label,
        addedAt = addedAt?.let(Instant::parse),
    )
}

private fun StoredCredential.toBridgeStoredCredential() = WalletBridgeStoredCredential(
    id = id,
    serializedCredential = credential.signed ?: credential.credentialData.toString(),
    format = credential.format,
    label = label,
    addedAt = addedAt?.toString(),
)

/**
 * Coarse error category for Swift bridge failures.
 */
@Serializable
enum class WalletBridgeErrorCategory {
    /** Input supplied by the caller is invalid. */
    invalidInput,

    /** Network communication failed. */
    network,

    /** Issuer-side processing failed. */
    issuer,

    /** Verifier-side processing failed. */
    verifier,

    /** Wallet storage access failed. */
    storage,

    /** Cryptographic operation failed. */
    crypto,

    /** Requested credential could not be found. */
    credentialNotFound,

    /** The operation was cancelled. */
    cancelled,

    /** Unexpected wallet failure that does not fit a narrower category. */
    internalFailure,
}

/**
 * Serializable error returned to Swift callers when a bridge operation fails.
 *
 * @property category Coarse failure category.
 * @property message Human-readable failure message.
 * @property causeClass Kotlin exception class name when available.
 */
@Serializable
data class WalletBridgeError(
    val category: WalletBridgeErrorCategory,
    val message: String,
    val causeClass: String? = null,
) {
    internal companion object {
        fun fromThrowable(throwable: Throwable): WalletBridgeError {
            val category = when (throwable) {
                is CancellationException -> WalletBridgeErrorCategory.cancelled
                is IllegalArgumentException -> WalletBridgeErrorCategory.invalidInput
                is WalletPersistenceException -> WalletBridgeErrorCategory.storage
                else -> WalletBridgeErrorCategory.internalFailure
            }

            return WalletBridgeError(
                category = category,
                message = throwable.message ?: throwable::class.simpleName ?: "Unknown wallet error",
                causeClass = throwable::class.simpleName,
            )
        }
    }
}

/**
 * Result wrapper used by Swift bridge operations.
 */
sealed interface WalletBridgeResult<out T> {
    /**
     * Successful bridge operation result.
     *
     * @property value Value returned by the wallet operation.
     */
    data class Success<T>(val value: T) : WalletBridgeResult<T>

    /**
     * Failed bridge operation result.
     *
     * @property error Structured bridge error returned to Swift callers.
     */
    data class Failure(val error: WalletBridgeError) : WalletBridgeResult<Nothing>
}
