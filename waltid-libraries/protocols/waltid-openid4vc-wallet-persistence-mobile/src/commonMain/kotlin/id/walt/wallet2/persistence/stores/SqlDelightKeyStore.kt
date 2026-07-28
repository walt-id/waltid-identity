package id.walt.wallet2.persistence.stores

import id.walt.crypto.keys.Key
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.StorableKey
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.Key as StoredKeyMaterial
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.crypto2.signum.SignumKeyPolicy
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.data.WalletKeyStoreEntry
import id.walt.wallet2.persistence.db.WalletPersistenceQueries
import id.walt.wallet2.persistence.keys.PlatformManagedKeyProvider
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * Wallet key store backed by SQLDelight.
 *
 * Every row persists the versioned [StoredKey] descriptor required to restore the key. Managed keys retain private
 * material in the native platform store; software keys retain their encoded material in the descriptor. This is a
 * fresh-schema store and deliberately does not read, repair, or write legacy key references.
 */
public class SqlDelightKeyStore(
    private val managedKeyProvider: PlatformManagedKeyProvider,
    private val queries: WalletPersistenceQueries,
) : WalletKeyStore {
    private val softwareRuntime = CryptoRuntime(defaultSoftwareKeyProviders())

    /** Legacy key material is not supported by the mobile store. */
    override suspend fun getKey(keyId: String): Key? = null

    /** Restores a persisted storable key with the requested usages. */
    override suspend fun getCrypto2Key(keyId: String, usages: Set<KeyUsage>): StoredKeyMaterial? =
        queries.selectByKeyId(keyId).executeAsOneOrNull()?.let { ref ->
            restoreStoredKey(decodeStoredKey(ref.key_id, ref.stored_key)).also { key ->
                require(usages.all(key.usages::contains)) { "Mobile key does not permit requested usages" }
            }
        }

    /** Restores a persisted key as a wallet key-store entry. */
    override suspend fun getKeyMaterial(keyId: String, usages: Set<KeyUsage>): WalletKeyStoreEntry? =
        getCrypto2Key(keyId, usages)?.let { WalletKeyStoreEntry(keyId, legacyKey = null, crypto2Key = it) }

    /** Lists the identifiers and specifications of all persisted keys. */
    override suspend fun listKeys(): Flow<WalletKeyInfo> = flow {
        queries.selectAll().executeAsList().forEach { ref ->
            val stored = decodeStoredKey(ref.key_id, ref.stored_key)
            emit(WalletKeyInfo(keyId = stored.id.value, keyType = stored.spec.toString()))
        }
    }

    /** Legacy keys must be converted to a storable key before they are persisted. */
    override suspend fun addKey(key: Key): String =
        throw UnsupportedOperationException("Mobile key storage supports storable keys only")

    /** Generates and persists a managed key. */
    public suspend fun generateManagedKey(
        id: KeyId,
        spec: KeySpec,
        usages: Set<KeyUsage>,
        policy: SignumKeyPolicy? = null,
    ): ManagedKey {
        require(queries.selectByKeyId(id.value).executeAsOneOrNull() == null) {
            "Mobile key already exists: ${id.value}"
        }
        val key = managedKeyProvider.generateManagedKey(id, spec, usages, policy)
        try {
            addCrypto2Key(key)
        } catch (cause: Throwable) {
            try {
                withContext(NonCancellable) {
                    managedKeyProvider.deleteManagedKey(key.storedKey)
                }
            } catch (cleanupFailure: Throwable) {
                cause.addSuppressed(cleanupFailure)
            }
            throw cause
        }
        return key
    }

    /** Persists a versioned descriptor without exporting it through the legacy key API. */
    override suspend fun addCrypto2Key(key: StoredKeyMaterial): String {
        val stored = (key as? StorableKey)?.storedKey
            ?: throw IllegalArgumentException("Mobile key persistence requires a storable key")
        require(key.id == stored.id && key.spec == stored.spec && key.usages == stored.usages) {
            "Key properties do not match the stored descriptor"
        }
        restoreStoredKey(stored)

        queries.insert(
            key_id = stored.id.value,
            created_at = Clock.System.now().toEpochMilliseconds(),
            stored_key = StoredKeyCodec.encodeToString(stored),
        )
        return stored.id.value
    }

    /** Removes a persisted key and any managed native key material it owns. */
    override suspend fun removeKey(keyId: String): Boolean {
        val ref = queries.selectByKeyId(keyId).executeAsOneOrNull() ?: return false
        when (val stored = decodeStoredKey(ref.key_id, ref.stored_key)) {
            is StoredKey.Managed -> managedKeyProvider.deleteManagedKey(stored)
            is StoredKey.Software -> Unit
        }
        queries.deleteByKeyId(keyId)
        return true
    }

    private fun decodeStoredKey(keyId: String, serialized: String): StoredKey =
        StoredKeyCodec.decodeFromString(serialized).also { stored ->
            require(stored.id == KeyId(keyId)) { "Stored key ID does not match mobile key reference" }
            require(stored.usages.isNotEmpty()) { "Stored key usages cannot be empty" }
        }

    private suspend fun restoreStoredKey(stored: StoredKey): StoredKeyMaterial = when (stored) {
        is StoredKey.Managed -> managedKeyProvider.restoreManagedKey(stored)
        is StoredKey.Software -> softwareRuntime.restore(stored)
    }.also { key ->
        require(key.id == stored.id) { "Restored key ID does not match its stored descriptor" }
        require(key.spec == stored.spec) { "Restored key specification does not match its stored descriptor" }
        require(key.usages == stored.usages) { "Restored key usages do not match its stored descriptor" }
    }
}
