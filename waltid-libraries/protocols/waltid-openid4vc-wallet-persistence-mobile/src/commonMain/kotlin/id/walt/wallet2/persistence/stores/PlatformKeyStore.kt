package id.walt.wallet2.persistence.stores

import id.walt.crypto.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.StorableKey
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.Key as ManagedKeyMaterial
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.crypto2.signum.SignumKeyPolicy
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.data.WalletKeyStoreEntry
import id.walt.wallet2.persistence.db.WalletPersistenceQueries
import id.walt.wallet2.persistence.keys.PlatformManagedKeyProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock

/**
 * Managed-key wallet store backed by SQLDelight.
 *
 * Every row persists the versioned [StoredKey] descriptor required to restore the key. Managed keys retain their
 * private material in the platform store. This is a fresh-schema store and deliberately does not read, repair, or
 * write legacy key references or software-key material.
 */
public class PlatformKeyStore(
    private val keyProvider: PlatformManagedKeyProvider,
    private val queries: WalletPersistenceQueries,
) : WalletKeyStore {
    /** The platform mobile store contains managed keys only. */
    override suspend fun getKey(keyId: String): Key? = null

    override suspend fun getCrypto2Key(keyId: String, usages: Set<KeyUsage>): ManagedKeyMaterial? =
        queries.selectByKeyId(keyId).executeAsOneOrNull()?.let { ref ->
            restoreStoredKey(decodeStoredKey(ref.key_id, ref.stored_key)).also { key ->
                require(usages.all(key.usages::contains)) { "Mobile key does not permit requested usages" }
            }
        }

    override suspend fun getKeyMaterial(keyId: String, usages: Set<KeyUsage>): WalletKeyStoreEntry? =
        getCrypto2Key(keyId, usages)?.let { WalletKeyStoreEntry(keyId, legacyKey = null, crypto2Key = it) }

    override suspend fun listKeys(): Flow<WalletKeyInfo> = flow {
        queries.selectAll().executeAsList().forEach { ref ->
            val stored = decodeStoredKey(ref.key_id, ref.stored_key)
            emit(WalletKeyInfo(keyId = stored.id.value, keyType = stored.spec.toString()))
        }
    }

    /** The platform mobile store never persists legacy keys. */
    override suspend fun addKey(key: Key): String =
        throw UnsupportedOperationException("Mobile platform key storage supports managed keys only")

    /** Generates and persists a managed key. */
    public suspend fun generateManagedKey(
        id: KeyId,
        spec: KeySpec,
        usages: Set<KeyUsage>,
        policy: SignumKeyPolicy? = null,
    ): ManagedKey {
        val key = keyProvider.generateManagedKey(id, spec, usages, policy)
        try {
            addCrypto2Key(key)
        } catch (cause: Throwable) {
            runCatching { keyProvider.deleteManagedKey(key.storedKey) }.exceptionOrNull()?.let(cause::addSuppressed)
            throw cause
        }
        return key
    }

    /** Persists a managed descriptor without exporting it through the legacy key API. */
    override suspend fun addCrypto2Key(key: ManagedKeyMaterial): String {
        val stored = (key as? StorableKey)?.storedKey
            ?: throw IllegalArgumentException("Mobile key persistence requires a storable key")
        require(key.id == stored.id && key.spec == stored.spec && key.usages == stored.usages) {
            "Key properties do not match the stored descriptor"
        }
        require(stored is StoredKey.Managed) {
            "Mobile platform key storage supports managed keys only"
        }
        restoreStoredKey(stored)

        queries.insert(
            key_id = stored.id.value,
            created_at = Clock.System.now().toEpochMilliseconds(),
            stored_key = StoredKeyCodec.encodeToString(stored),
        )
        return stored.id.value
    }

    override suspend fun removeKey(keyId: String): Boolean {
        val ref = queries.selectByKeyId(keyId).executeAsOneOrNull() ?: return false
        keyProvider.deleteManagedKey(decodeStoredKey(ref.key_id, ref.stored_key))
        queries.deleteByKeyId(keyId)
        return true
    }

    private fun decodeStoredKey(keyId: String, serialized: String): StoredKey.Managed =
        (StoredKeyCodec.decodeFromString(serialized) as? StoredKey.Managed)?.also { stored ->
            require(stored.id == KeyId(keyId)) { "Stored key ID does not match mobile key reference" }
            require(stored.usages.isNotEmpty()) { "Stored key usages cannot be empty" }
        } ?: throw IllegalArgumentException("Mobile platform key storage requires a managed descriptor")

    private suspend fun restoreStoredKey(stored: StoredKey.Managed): ManagedKeyMaterial = keyProvider.restoreManagedKey(stored)
}
