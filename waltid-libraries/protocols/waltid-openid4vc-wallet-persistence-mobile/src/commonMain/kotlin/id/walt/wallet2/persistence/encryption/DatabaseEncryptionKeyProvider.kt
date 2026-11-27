package id.walt.wallet2.persistence.encryption

/**
 * Provides database encryption keys for mobile wallet persistence.
 */
public interface DatabaseEncryptionKeyProvider {

    /**
     * Returns the existing encryption key for [databaseName] or creates one if this provider owns creation.
     */
    public suspend fun getOrCreateKey(walletId: String, databaseName: String): DatabaseEncryptionKey

    /**
     * Deletes provider-owned key material for [databaseName], if present.
     */
    public suspend fun deleteKey(walletId: String, databaseName: String)
}
