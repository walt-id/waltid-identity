package id.walt.wallet2.persistence.encryption

/**
 * Provides database encryption keys for mobile wallet persistence.
 */
interface DatabaseEncryptionKeyProvider {

    /**
     * Returns the existing encryption key for [databaseName] or creates one if this provider owns creation.
     */
    suspend fun getOrCreateKey(walletId: String, databaseName: String): DatabaseEncryptionKey

    /**
     * Deletes provider-owned key material for [databaseName], if present.
     */
    suspend fun deleteKey(walletId: String, databaseName: String)
}

/**
 * Raw key material used to unlock one encrypted wallet database.
 *
 * @property keyId Stable identifier for the database key.
 * @property material Defensive copy of the raw SQLCipher key material.
 */
class DatabaseEncryptionKey(
    val keyId: String,
    material: ByteArray,
) {
    private val materialBytes: ByteArray = material.copyOf()

    /**
     * Raw SQLCipher key material.
     */
    val material: ByteArray get() = materialBytes.copyOf()

    /**
     * Returns a new key value with optional replacements.
     */
    fun copy(
        keyId: String = this.keyId,
        material: ByteArray = this.material,
    ) = DatabaseEncryptionKey(keyId, material)

    /**
     * Destructures the key identifier.
     */
    operator fun component1(): String = keyId

    /**
     * Destructures a defensive copy of the key material.
     */
    operator fun component2(): ByteArray = material.copyOf()

    /**
     * Compares key identifiers and key bytes.
     */
    override fun equals(other: Any?): Boolean =
        other is DatabaseEncryptionKey &&
            keyId == other.keyId &&
            materialBytes.contentEquals(other.materialBytes)

    /**
     * Hashes the key identifier and key bytes.
     */
    override fun hashCode(): Int =
        31 * keyId.hashCode() + materialBytes.contentHashCode()

    /**
     * Formats the key without exposing raw key material.
     */
    override fun toString(): String =
        "DatabaseEncryptionKey(keyId=$keyId, material=<redacted>)"
}

/**
 * Base exception for mobile wallet persistence encryption failures.
 */
sealed class WalletPersistenceException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    /**
     * The encrypted wallet database or stored database key could not be unlocked.
     */
    class DatabaseUnlockFailed(walletId: String, cause: Throwable? = null) :
        WalletPersistenceException("Wallet '$walletId' database could not be unlocked", cause)

    /**
     * Encrypted persistence is unavailable or incorrectly linked/configured on this platform.
     */
    class EncryptionConfigurationFailed(walletId: String, cause: Throwable? = null) :
        WalletPersistenceException("Wallet '$walletId' encrypted persistence is not configured correctly", cause)

    /**
     * A wallet database exists but the SDK-managed key needed to open it is missing.
     */
    class DatabaseKeyMissing(walletId: String) :
        WalletPersistenceException("Wallet '$walletId' database exists but its SDK-managed database key is missing")
}
