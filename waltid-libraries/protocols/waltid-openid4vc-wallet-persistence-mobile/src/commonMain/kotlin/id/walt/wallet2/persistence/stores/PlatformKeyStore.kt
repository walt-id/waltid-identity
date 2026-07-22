package id.walt.wallet2.persistence.stores

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.StorableKey
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.crypto2.signum.SignumKeyPolicy
import id.walt.wallet2.persistence.db.WalletPersistenceQueries
import id.walt.wallet2.persistence.keys.Crypto2PlatformKeyProvider
import id.walt.wallet2.persistence.keys.MobileStoredKeyMigration
import id.walt.wallet2.persistence.keys.PlatformKeyProvider
import id.walt.wallet2.persistence.keys.toCrypto2KeySpec
import id.walt.wallet2.persistence.keys.toLegacyKeyType
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.data.WalletKeyStoreEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * Wallet key store that persists key metadata in SQLDelight.
 *
 * Platform-backed keys keep their material in the platform key store. Legacy software keys keep their serialized
 * material in the compatibility column, while crypto2-native software keys keep JWK material in [StoredKey].
 *
 * @param keyProvider Platform key provider used to load, export, and delete keys.
 * @param queries SQLDelight queries for wallet persistence tables.
 */
public class PlatformKeyStore(
    private val keyProvider: PlatformKeyProvider,
    private val queries: WalletPersistenceQueries,
) : WalletKeyStore {
    private val crypto2Provider = keyProvider as? Crypto2PlatformKeyProvider
    private val crypto2Migration = MobileStoredKeyMigration(crypto2Provider)

    /**
     * Loads a wallet key by its wallet-local key identifier.
     */
    override suspend fun getKey(keyId: String): Key? {
        val ref = queries.selectByKeyId(keyId).executeAsOneOrNull() ?: return null
        val keyType = KeyType.valueOf(ref.key_type)
        restoreOrBackfillCrypto2Key(
            keyId = ref.key_id,
            keyType = keyType,
            platformBacked = ref.is_platform_backed == 1L,
            keyMaterial = ref.key_material,
            serialized = ref.crypto2_stored_key,
        )
        return loadLegacyKey(keyId, keyType, ref.is_platform_backed == 1L, ref.key_material)
    }

    override suspend fun getCrypto2Key(keyId: String, usages: Set<KeyUsage>): Crypto2Key? {
        val ref = queries.selectByKeyId(keyId).executeAsOneOrNull() ?: return null
        return restoreOrBackfillCrypto2Key(
            keyId = ref.key_id,
            keyType = KeyType.valueOf(ref.key_type),
            platformBacked = ref.is_platform_backed == 1L,
            keyMaterial = ref.key_material,
            serialized = ref.crypto2_stored_key,
        )?.also { key ->
            require(usages.all(key.usages::contains)) { "Mobile crypto2 key does not permit requested usages" }
        }
    }

    override suspend fun getKeyMaterial(keyId: String, usages: Set<KeyUsage>): WalletKeyStoreEntry? {
        val ref = queries.selectByKeyId(keyId).executeAsOneOrNull() ?: return null
        val keyType = KeyType.valueOf(ref.key_type)
        val platformBacked = ref.is_platform_backed == 1L
        val crypto2Key = restoreOrBackfillCrypto2Key(
            keyId = ref.key_id,
            keyType = keyType,
            platformBacked = platformBacked,
            keyMaterial = ref.key_material,
            serialized = ref.crypto2_stored_key,
        )?.also { key ->
            require(usages.all(key.usages::contains)) { "Mobile crypto2 key does not permit requested usages" }
        }
        val legacyKey = loadLegacyKey(keyId, keyType, platformBacked, ref.key_material)
        return if (legacyKey != null || crypto2Key != null) {
            WalletKeyStoreEntry(keyId, legacyKey, crypto2Key)
        } else null
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
        val isPlatformBacked = keyProvider.isPlatformBacked(key)
        val material = if (!isPlatformBacked) keyProvider.exportSoftwareKeyMaterial(key) else null
        val stored = crypto2Migration.migrate(
            id = KeyId(keyId),
            keyType = key.keyType,
            platformBacked = isPlatformBacked,
            keyMaterial = material?.decodeToString(),
            usages = KEY_USAGES,
        )

        queries.insert(
            key_id = keyId,
            key_type = key.keyType.name,
            created_at = Clock.System.now().toEpochMilliseconds(),
            is_platform_backed = if (isPlatformBacked) 1L else 0L,
            key_material = material?.decodeToString(),
            crypto2_stored_key = stored?.let(StoredKeyCodec::encodeToString),
        )
        return keyId
    }

    /**
     * Generates and persists a managed crypto2 key without routing through the legacy key API.
     */
    public suspend fun generateCrypto2Key(
        id: KeyId,
        spec: KeySpec,
        usages: Set<KeyUsage>,
        policy: SignumKeyPolicy? = null,
    ): ManagedKey {
        spec.toLegacyKeyType()
        val provider = requireNotNull(crypto2Provider) {
            "The configured mobile key provider does not support managed crypto2 keys"
        }
        val key = provider.generateManagedKey(id, spec, usages, policy)
        try {
            addCrypto2Key(key)
        } catch (cause: Throwable) {
            try {
                withContext(NonCancellable) { provider.deleteManagedKey(key.storedKey) }
            } catch (cleanupFailure: Throwable) {
                cause.addSuppressed(cleanupFailure)
            }
            throw cause
        }
        return key
    }

    /**
     * Persists a crypto2 key's versioned descriptor without exporting it through the legacy key API.
     */
    override suspend fun addCrypto2Key(key: Crypto2Key): String {
        val stored = (key as? StorableKey)?.storedKey
            ?: throw IllegalArgumentException("Mobile crypto2 persistence requires a storable key")
        require(key.id == stored.id && key.spec == stored.spec && key.usages == stored.usages) {
            "Crypto2 key properties do not match its stored descriptor"
        }
        val keyType = stored.spec.toLegacyKeyType()
        when (stored) {
            is StoredKey.Managed -> requireNotNull(crypto2Provider) {
                "The configured mobile key provider cannot restore managed crypto2 keys"
            }
            is StoredKey.Software -> require(stored.material is EncodedKey.Jwk) {
                "Mobile crypto2 persistence supports software keys stored as JWK only"
            }
        }
        crypto2Migration.restore(stored)

        queries.insert(
            key_id = stored.id.value,
            key_type = keyType.name,
            created_at = Clock.System.now().toEpochMilliseconds(),
            is_platform_backed = if (stored is StoredKey.Managed) 1L else 0L,
            key_material = null,
            crypto2_stored_key = StoredKeyCodec.encodeToString(stored),
        )
        return stored.id.value
    }

    /**
     * Removes the platform-backed key when present and deletes its SQLDelight key reference.
     */
    override suspend fun removeKey(keyId: String): Boolean {
        val ref = queries.selectByKeyId(keyId).executeAsOneOrNull() ?: return false
        val keyType = KeyType.valueOf(ref.key_type)
        val keyDeleted = try {
            val stored = ref.crypto2_stored_key?.let(StoredKeyCodec::decodeFromString)?.also {
                validateStoredKey(it, ref.key_id, keyType, ref.is_platform_backed == 1L)
            }
            when (stored) {
                is StoredKey.Managed -> {
                    crypto2Migration.delete(stored)
                    true
                }
                is StoredKey.Software -> true
                null -> ref.is_platform_backed != 1L || keyProvider.deleteKey(keyId, keyType)
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
            ref.is_platform_backed != 1L || keyProvider.deleteKey(keyId, keyType)
        }
        if (!keyDeleted) return false
        queries.deleteByKeyId(keyId)
        return true
    }

    private suspend fun restoreOrBackfillCrypto2Key(
        keyId: String,
        keyType: KeyType,
        platformBacked: Boolean,
        keyMaterial: String?,
        serialized: String?,
    ): Crypto2Key? {
        var stored = serialized?.let(StoredKeyCodec::decodeFromString)
        if (stored == null) {
            stored = crypto2Migration.migrate(
                id = KeyId(keyId),
                keyType = keyType,
                platformBacked = platformBacked,
                keyMaterial = keyMaterial,
                usages = KEY_USAGES,
            )
            stored?.let { queries.updateCrypto2StoredKey(StoredKeyCodec.encodeToString(it), keyId) }
        } else if (!platformBacked && keyMaterial != null) {
            val expected = crypto2Migration.migrate(
                id = KeyId(keyId),
                keyType = keyType,
                platformBacked = false,
                keyMaterial = keyMaterial,
                usages = KEY_USAGES,
            )
            if (expected != null && stored != expected) {
                stored = expected
                queries.updateCrypto2StoredKey(StoredKeyCodec.encodeToString(expected), keyId)
            }
        }
        stored ?: return null
        validateStoredKey(stored, keyId, keyType, platformBacked)
        return crypto2Migration.restore(stored)
    }

    private fun validateStoredKey(
        stored: StoredKey,
        keyId: String,
        keyType: KeyType,
        platformBacked: Boolean,
    ) {
        require(stored.id == KeyId(keyId)) { "Stored crypto2 key ID does not match mobile key reference" }
        require(stored.spec == keyType.toCrypto2KeySpec()) { "Stored crypto2 key spec does not match mobile key reference" }
        require(stored.usages.isNotEmpty()) { "Stored crypto2 key usages cannot be empty" }
        require(platformBacked == (stored is StoredKey.Managed)) {
            "Stored crypto2 key kind does not match mobile key backing"
        }
    }

    private suspend fun loadLegacyKey(
        keyId: String,
        keyType: KeyType,
        platformBacked: Boolean,
        keyMaterial: String?,
    ): Key? = if (platformBacked) {
        keyProvider.loadKey(keyId, keyType)
    } else {
        keyMaterial?.let { keyProvider.loadSoftwareKey(keyId, keyType, it.encodeToByteArray()) }
    }

    private companion object {
        val KEY_USAGES = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
    }
}
