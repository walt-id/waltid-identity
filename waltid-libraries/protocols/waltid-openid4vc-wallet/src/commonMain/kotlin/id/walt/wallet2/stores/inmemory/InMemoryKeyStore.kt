package id.walt.wallet2.stores.inmemory

import id.walt.crypto.keys.Key
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.data.WalletKeyStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

/**
 * In-memory [id.walt.wallet2.data.WalletKeyStore].
 *
 * Suitable for development, testing, and mobile apps.
 * Not suitable for production deployments requiring persistence across restarts.
 */
class InMemoryKeyStore : WalletKeyStore {

    private val keys = mutableMapOf<String, Key>()

    override suspend fun getKey(keyId: String): Key? = keys[keyId]

    override fun listKeys(): Flow<WalletKeyInfo> =
        keys.entries.map { (id, key) ->
            WalletKeyInfo(keyId = id, keyType = key.keyType.name)
        }.asFlow()

    override suspend fun addKey(key: Key): String {
        val id = key.getKeyId()
        keys[id] = key
        return id
    }

    override suspend fun removeKey(keyId: String): Boolean =
        keys.remove(keyId) != null
}
