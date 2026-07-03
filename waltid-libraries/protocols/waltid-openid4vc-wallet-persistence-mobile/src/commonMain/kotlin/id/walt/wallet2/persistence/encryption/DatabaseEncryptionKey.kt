package id.walt.wallet2.persistence.encryption

/**
 * Provides database encryption keys for mobile wallet persistence.
 */
interface DatabaseEncryptionKeyProvider {

    suspend fun getOrCreateKey(walletId: String, databaseName: String): DatabaseEncryptionKey

    suspend fun deleteKey(walletId: String, databaseName: String)
}

/**
 * Raw key material used to unlock one encrypted wallet database.
 */
class DatabaseEncryptionKey(
    val keyId: String,
    material: ByteArray,
) {
    private val materialBytes: ByteArray = material.copyOf()

    val material: ByteArray get() = materialBytes.copyOf()

    fun copy(
        keyId: String = this.keyId,
        material: ByteArray = this.material,
    ) = DatabaseEncryptionKey(keyId, material)

    operator fun component1(): String = keyId

    operator fun component2(): ByteArray = material.copyOf()

    override fun equals(other: Any?): Boolean =
        other is DatabaseEncryptionKey &&
            keyId == other.keyId &&
            materialBytes.contentEquals(other.materialBytes)

    override fun hashCode(): Int =
        31 * keyId.hashCode() + materialBytes.contentHashCode()

    override fun toString(): String =
        "DatabaseEncryptionKey(keyId=$keyId, material=<redacted>)"
}

sealed class WalletPersistenceException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    class DatabaseUnlockFailed(walletId: String, cause: Throwable? = null) :
        WalletPersistenceException("Wallet '$walletId' database could not be unlocked", cause)

    class EncryptionConfigurationFailed(walletId: String, cause: Throwable? = null) :
        WalletPersistenceException("Wallet '$walletId' encrypted persistence is not configured correctly", cause)

    class DatabaseKeyMissing(walletId: String) :
        WalletPersistenceException("Wallet '$walletId' database exists but its SDK-managed database key is missing")
}
