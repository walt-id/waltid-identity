package id.walt.wallet2.stores.inmemory

import id.walt.crypto.keys.Key
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.Key as Crypto2Key
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
    private val crypto2Keys = mutableMapOf<String, Crypto2Key>()

    override suspend fun getKey(keyId: String): Key? = keys[keyId]

    override suspend fun getCrypto2Key(keyId: String, usages: Set<KeyUsage>): Crypto2Key? =
        crypto2Keys[keyId]?.also { key ->
            require(usages.all(key.usages::contains)) { "Wallet crypto2 key does not permit requested usages" }
        }

    override suspend fun listKeys(): Flow<WalletKeyInfo> =
        (keys.keys + crypto2Keys.keys).distinct().map { id ->
            WalletKeyInfo(
                keyId = id,
                keyType = keys[id]?.keyType?.name ?: requireNotNull(crypto2Keys[id]).spec.toString(),
            )
        }.asFlow()

    override suspend fun addKey(key: Key): String {
        val id = key.getKeyId()
        keys[id] = key
        return id
    }

    override suspend fun addCrypto2Key(key: Crypto2Key): String = key.id.value.also { id ->
        crypto2Keys[id] = key
    }

    override suspend fun removeKey(keyId: String): Boolean {
        val legacyRemoved = keys.remove(keyId) != null
        val crypto2Removed = crypto2Keys.remove(keyId) != null
        return legacyRemoved || crypto2Removed
    }
}
