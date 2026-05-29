package id.walt.wallet2.client.stores

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.wallet2.client.db.WalletClientQueries
import id.walt.wallet2.client.keys.PlatformKeyProvider
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.data.WalletKeyStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock

class HardwareKeyStore(
    private val keyProvider: PlatformKeyProvider,
    private val queries: WalletClientQueries,
) : WalletKeyStore {

    override suspend fun getKey(keyId: String): Key? {
        val ref = queries.selectByKeyId(keyId).executeAsOneOrNull() ?: return null
        val keyType = KeyType.valueOf(ref.key_type)
        return keyProvider.loadKey(keyId, keyType)
    }

    override suspend fun listKeys(): Flow<WalletKeyInfo> = flow {
        queries.selectAll().executeAsList().forEach { ref ->
            emit(WalletKeyInfo(keyId = ref.key_id, keyType = ref.key_type))
        }
    }

    override suspend fun addKey(key: Key): String {
        val keyId = key.getKeyId()
        queries.insert(
            key_id = keyId,
            key_type = key.keyType.name,
            created_at = Clock.System.now().toEpochMilliseconds(),
            is_hardware_backed = 1L,
        )
        return keyId
    }

    override suspend fun removeKey(keyId: String): Boolean {
        val ref = queries.selectByKeyId(keyId).executeAsOneOrNull() ?: return false
        val keyType = KeyType.valueOf(ref.key_type)
        keyProvider.deleteKey(keyId, keyType)
        queries.deleteByKeyId(keyId)
        return true
    }
}
