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

/**
 * Wallet key store that persists key metadata in SQLDelight.
 *
 * Platform-backed keys keep their material in the platform key store. Software keys store serialized key
 * material in the SQLDelight table so they can be reloaded on platforms that require a fallback.
 *
 * @param keyProvider Platform key provider used to load, export, and delete keys.
 * @param queries SQLDelight queries for wallet persistence tables.
 */
public class PlatformKeyStore(
    private val keyProvider: PlatformKeyProvider,
    private val queries: WalletPersistenceQueries,
) : WalletKeyStore {

    /**
     * Loads a wallet key by its wallet-local key identifier.
     */
    override suspend fun getKey(keyId: String): Key? {
        val ref = queries.selectByKeyId(keyId).executeAsOneOrNull() ?: return null
        val keyType = KeyType.valueOf(ref.key_type)
        return if (ref.is_platform_backed == 1L) {
            keyProvider.loadKey(keyId, keyType)
        } else {
            val material = ref.key_material?.encodeToByteArray()
                ?: error("Software key '$keyId' has no stored key material")
            keyProvider.loadSoftwareKey(keyId, keyType, material)
        }
    }

    /**
     * Streams all persisted key references.
     */
    override suspend fun listKeys(): Flow<WalletKeyInfo> = flow {
        queries.selectAll().executeAsList().forEach { ref ->
            emit(WalletKeyInfo(keyId = ref.key_id, keyType = ref.key_type))
        }
    }

    /**
     * Persists a key reference for [key].
     *
     * Platform-backed key material remains in the platform key store. Software key material is serialized
     * into the SQLDelight table.
     */
    override suspend fun addKey(key: Key): String {
        val keyId = key.getKeyId()
        val isPlatformBacked = keyProvider.isPlatformBacked(key.keyType)
        val material = if (!isPlatformBacked) keyProvider.exportSoftwareKeyMaterial(key) else null

        queries.insert(
            key_id = keyId,
            key_type = key.keyType.name,
            created_at = Clock.System.now().toEpochMilliseconds(),
            is_platform_backed = if (isPlatformBacked) 1L else 0L,
            key_material = material?.decodeToString(),
        )
        return keyId
    }

    /**
     * Removes the platform-backed key when present and deletes its SQLDelight key reference.
     */
    override suspend fun removeKey(keyId: String): Boolean {
        val ref = queries.selectByKeyId(keyId).executeAsOneOrNull() ?: return false
        val keyType = KeyType.valueOf(ref.key_type)
        keyProvider.deleteKey(keyId, keyType)
        queries.deleteByKeyId(keyId)
        return true
    }
}
