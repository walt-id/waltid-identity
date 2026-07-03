package id.walt.wallet2.mobile.swiftinterop

import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletKeyType
import id.walt.wallet2.mobile.MobileWalletPersistenceConfig
import id.walt.wallet2.mobile.WalletAttestationConfig
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKeyProvider
import id.walt.wallet2.persistence.encryption.WalletPersistenceException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

/**
 * Configuration used when creating an iOS [WalletSdkBridge].
 *
 * @property walletId Stable wallet identifier used for database naming and persisted wallet state.
 * @property defaultKeyType Key type used by wallet bootstrap when no key type override is supplied.
 * @property persistence Persistence mode used for wallet-local state.
 * @property databaseKeyProvider Swift-owned database key provider used when [persistence] is
 * [WalletBridgePersistenceConfiguration.IntegratorManagedKey].
 * @property attestation Optional client-attestation configuration for issuers that require it.
 */
public data class WalletBridgeConfiguration(
    val walletId: String = "default",
    val defaultKeyType: MobileWalletKeyType = MobileWalletKeyType.secp256r1,
    val persistence: WalletBridgePersistenceConfiguration = WalletBridgePersistenceConfiguration.SdkManagedEncrypted,
    val databaseKeyProvider: WalletBridgeDatabaseEncryptionKeyProvider? = null,
    val attestation: WalletAttestationConfig? = null,
)

internal fun WalletBridgeConfiguration.toMobileWalletConfig() = MobileWalletConfig(
    walletId = walletId,
    defaultKeyType = defaultKeyType,
    attestationConfig = attestation,
    persistence = persistence.toMobileWalletPersistenceConfig(databaseKeyProvider),
)

/**
 * Persistence modes exposed to the Swift wallet bridge.
 */
enum class WalletBridgePersistenceConfiguration {
    /** SDK-managed encrypted SQLDelight persistence. */
    SdkManagedEncrypted,

    /** Encrypted SQLDelight persistence with database keys supplied by Swift app code. */
    IntegratorManagedKey,
}

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
 * Swift-facing provider for integrator-managed encrypted wallet database keys.
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

private fun WalletBridgePersistenceConfiguration.toMobileWalletPersistenceConfig(
    databaseKeyProvider: WalletBridgeDatabaseEncryptionKeyProvider?,
): MobileWalletPersistenceConfig =
    when (this) {
        WalletBridgePersistenceConfiguration.SdkManagedEncrypted ->
            MobileWalletPersistenceConfig.SdkManagedEncrypted

        WalletBridgePersistenceConfiguration.IntegratorManagedKey ->
            MobileWalletPersistenceConfig.IntegratorManagedKey(
                keyProvider = BridgeDatabaseEncryptionKeyProvider(
                    databaseKeyProvider
                        ?: throw IllegalArgumentException("Integrator-managed persistence requires a database key provider"),
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

/**
 * Coarse error category for Swift bridge failures.
 */
@Serializable
public enum class WalletBridgeErrorCategory {
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
public data class WalletBridgeError(
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
public sealed interface WalletBridgeResult<out T> {
    /**
     * Successful bridge operation result.
     *
     * @property value Value returned by the wallet operation.
     */
    public data class Success<T>(public val value: T) : WalletBridgeResult<T>

    /**
     * Failed bridge operation result.
     *
     * @property error Structured bridge error returned to Swift callers.
     */
    public data class Failure(public val error: WalletBridgeError) : WalletBridgeResult<Nothing>
}
