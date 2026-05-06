package id.walt.wallet2.data

import id.walt.crypto.keys.Key

/**
 * Storage contract for cryptographic keys held by a wallet.
 */
interface WalletKeyStore {

    suspend fun getKey(keyId: String): Key?

    suspend fun listKeys(): List<WalletKeyInfo>

    /**
     * Persists a key and returns its assigned ID.
     * The implementation decides the ID strategy (e.g. JWK thumbprint).
     */
    suspend fun addKey(key: Key): String

    /** Removes the key with the given keyId. Returns true if it existed. */
    suspend fun removeKey(keyId: String): Boolean

    suspend fun getDefaultKey(): Key? =
        listKeys().firstOrNull()?.let { getKey(it.keyId) }
}
