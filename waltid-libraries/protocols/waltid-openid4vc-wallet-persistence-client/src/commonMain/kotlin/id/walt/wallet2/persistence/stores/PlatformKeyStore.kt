package id.walt.wallet2.persistence.stores

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.wallet2.persistence.db.WalletPersistenceQueries
import id.walt.wallet2.persistence.keys.PlatformKeyProvider
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.data.WalletKeyStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock

class PlatformKeyStore(
    private val keyProvider: PlatformKeyProvider,
    private val queries: WalletPersistenceQueries,
) : WalletKeyStore {

    override suspend fun getKey(keyId: String): Key? {
        val ref = queries.selectByKeyId(keyId).executeAsOneOrNull() ?: return null
        val keyType = KeyType.valueOf(ref.key_type)
        return if (ref.is_hardware_backed == 1L) {
            keyProvider.loadKey(keyId, keyType)
        } else {
            val material = ref.key_material?.encodeToByteArray()
                ?: error("Software key '$keyId' has no stored key material")
            keyProvider.loadSoftwareKey(keyId, keyType, material)
        }
    }

    override suspend fun listKeys(): Flow<WalletKeyInfo> = flow {
        queries.selectAll().executeAsList().forEach { ref ->
            emit(WalletKeyInfo(keyId = ref.key_id, keyType = ref.key_type))
        }
    }

    override suspend fun addKey(key: Key): String {
        val keyId = key.getKeyId()
        val isHardware = keyProvider.isHardwareBacked(key.keyType)
        val material = if (!isHardware) keyProvider.exportSoftwareKeyMaterial(key) else null

        queries.insert(
            key_id = keyId,
            key_type = key.keyType.name,
            created_at = Clock.System.now().toEpochMilliseconds(),
            is_hardware_backed = if (isHardware) 1L else 0L,
            key_material = material?.decodeToString(),
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
