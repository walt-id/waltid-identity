package id.walt.wallet2.data

import id.walt.crypto.keys.Key
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.crypto2.keys.KeyUsage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList

/**
 * Storage contract for cryptographic keys held by a wallet.
 */
interface WalletKeyStore {

    suspend fun getKey(keyId: String): Key?

    suspend fun getCrypto2Key(keyId: String, usages: Set<KeyUsage> = emptySet()): Crypto2Key? = null

    suspend fun getKeyMaterial(keyId: String, usages: Set<KeyUsage> = emptySet()): WalletKeyStoreEntry? {
        val crypto2Key = getCrypto2Key(keyId, usages)
        val legacyKey = getKey(keyId)
        return if (legacyKey != null || crypto2Key != null) {
            WalletKeyStoreEntry(keyId, legacyKey, crypto2Key)
        } else null
    }

    /** Streams all key metadata entries. Uses Flow for consistency with credential/DID stores. */
    suspend fun listKeys(): Flow<WalletKeyInfo>

    /**
     * Persists a key and returns its assigned ID.
     * The implementation decides the ID strategy (e.g. key thumbprint).
     */
    suspend fun addKey(key: Key): String

    suspend fun addCrypto2Key(key: Crypto2Key): String =
        throw UnsupportedOperationException("This wallet key store does not support crypto2-only keys")

    /** Removes the key with the given keyId. Returns true if it existed. */
    suspend fun removeKey(keyId: String): Boolean

    suspend fun getDefaultKey(): Key? =
        listKeys().firstOrNull()?.let { getKey(it.keyId) }

    suspend fun getDefaultCrypto2Key(usages: Set<KeyUsage> = emptySet()): Crypto2Key? =
        listKeys().toList().firstNotNullOfOrNull { getCrypto2Key(it.keyId, usages) }

    /** Convenience: collect all keys as a list. */
    suspend fun listKeysAsList(): List<WalletKeyInfo> =
        listKeys().toList()
}

data class WalletKeyStoreEntry(
    val keyId: String,
    val legacyKey: Key?,
    val crypto2Key: Crypto2Key?,
) {
    fun requireCrypto2Key(): Crypto2Key =
        requireNotNull(crypto2Key) { "Key '$keyId' is not available through crypto2" }
}
