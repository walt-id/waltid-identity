package id.walt.wallet2.data

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyUseAuthorizationException
import id.walt.crypto.keys.KeyUseAuthorizationFailure
import id.walt.crypto.keys.KeyUseAuthorizationPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList

/**
 * Storage contract for cryptographic keys held by a wallet.
 */
interface WalletKeyStore {

    /** Whether this store preserves and enforces protected key-use authorization metadata. */
    val supportsKeyUseAuthorizationMetadata: Boolean
        get() = false

    suspend fun getKey(keyId: String): Key?

    /** Streams all key metadata entries. Uses Flow for consistency with credential/DID stores. */
    suspend fun listKeys(): Flow<WalletKeyInfo>

    /**
     * Persists a key and returns its assigned ID.
     * The implementation decides the ID strategy (e.g. key thumbprint).
     */
    suspend fun addKey(key: Key): String

    /**
     * Persists [key] with non-secret metadata known by the creation caller.
     *
     * Existing stores keep their historical behavior for unprotected keys through the default implementation.
     * Stores that support protected keys must override this method and [supportsKeyUseAuthorizationMetadata].
     */
    suspend fun addKey(key: Key, keyInfo: WalletKeyInfo): String {
        if (
            keyInfo.requestedKeyUseAuthorizationPolicy != KeyUseAuthorizationPolicy.None ||
            keyInfo.effectiveKeyUseAuthorizationPolicy != KeyUseAuthorizationPolicy.None
        ) {
            throw KeyUseAuthorizationException(
                failure = KeyUseAuthorizationFailure.UnsupportedCombination,
                message = "This key store does not preserve protected key-use authorization metadata",
            )
        }
        return addKey(key)
    }

    /** Removes the key with the given keyId. Returns true if it existed. */
    suspend fun removeKey(keyId: String): Boolean

    suspend fun getDefaultKey(): Key? =
        listKeys().firstOrNull()?.let { getKey(it.keyId) }

    /** Convenience: collect all keys as a list. */
    suspend fun listKeysAsList(): List<WalletKeyInfo> =
        listKeys().toList()
}
