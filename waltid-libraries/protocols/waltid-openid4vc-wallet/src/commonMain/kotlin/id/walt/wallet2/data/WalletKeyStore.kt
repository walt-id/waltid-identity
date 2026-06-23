package id.walt.wallet2.data

import id.walt.crypto.keys.Key
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList

/**
 * Storage contract for cryptographic keys held by a wallet.
 */
interface WalletKeyStore {

    suspend fun getKey(keyId: String): Key?

    /** Streams all key metadata entries. Uses Flow for consistency with credential/DID stores. */
    suspend fun listKeys(): Flow<WalletKeyInfo>

    /**
     * Persists a key and returns its assigned ID.
     * The implementation decides the ID strategy (e.g. key thumbprint).
     */
    suspend fun addKey(key: Key): String

    /** Removes the key with the given keyId. Returns true if it existed. */
    suspend fun removeKey(keyId: String): Boolean

    suspend fun getDefaultKey(): Key? =
        listKeys().firstOrNull()?.let { getKey(it.keyId) }

    /** Convenience: collect all keys as a list. */
    suspend fun listKeysAsList(): List<WalletKeyInfo> =
        listKeys().toList()
}
