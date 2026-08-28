package id.walt.wallet2.persistence

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.StorableKey
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.migration.v1.V1KeyMigration
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.data.WalletKeyStoreEntry
import id.walt.wallet2.data.WalletPublicKeyMaterial
import id.walt.wallet2.data.WalletKeyUsageUnsupportedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import id.walt.crypto2.keys.Key as Crypto2Key

/**
 * Exposed-backed [WalletKeyStore].
 *
 * The canonical persisted representation is the versioned crypto2 [StoredKey] descriptor in
 * [Wallet2Tables.Keys.crypto2StoredKey], so both software and managed (KMS/HSM) keys can be stored
 * without a serializable legacy [Key]. The legacy [Wallet2Tables.Keys.serializedKey] exists purely
 * for migration: rows written before crypto2 carry only it, and are migrated to a descriptor on first
 * read. It is never used to correct or replace an existing descriptor - a malformed descriptor fails
 * loudly instead of silently downgrading to the legacy representation.
 *
 * @param cryptoRuntime restores persisted descriptors. The default carries software providers only;
 *   pass a runtime holding the matching [id.walt.crypto2.providers.ManagedKeyProvider] to store and
 *   restore managed keys.
 */
class ExposedKeyStore(
    val storeId: String,
    private val db: Database,
    private val cryptoRuntime: CryptoRuntime = CryptoRuntime(defaultSoftwareKeyProviders()),
) : WalletKeyStore {
    private val migration = V1KeyMigration()

    override suspend fun getKey(keyId: String): Key? = getKeyMaterial(keyId)?.legacyKey

    override suspend fun getCrypto2Key(keyId: String, usages: Set<KeyUsage>): Crypto2Key? =
        getKeyMaterial(keyId, usages)?.crypto2Key

    override suspend fun getPublicKeyMaterial(keyId: String): WalletPublicKeyMaterial? =
        suspendTransaction(db) {
            val row = selectKey(keyId) ?: return@suspendTransaction null
            val stored = row[Wallet2Tables.Keys.crypto2StoredKey]
                ?.let(StoredKeyCodec::decodeFromString)
                ?: row[Wallet2Tables.Keys.serializedKey]?.let { serialized ->
                    migrateSerializedDescriptor(row[Wallet2Tables.Keys.keyId], serialized)
                }
            when (stored) {
                is StoredKey.Software -> stored.material.toPublicJwk(stored.spec)
                is StoredKey.Managed -> stored.publicKey?.toPublicJwk(stored.spec)
                null -> null
            }?.let(::WalletPublicKeyMaterial)
        }

    override suspend fun getKeyMaterial(keyId: String, usages: Set<KeyUsage>): WalletKeyStoreEntry? =
        suspendTransaction(db) {
            val row = selectKey(keyId) ?: return@suspendTransaction null
            val crypto2Key = resolveCrypto2Key(row)?.also { key ->
                if (!usages.all(key.usages::contains)) {
                    throw WalletKeyUsageUnsupportedException("Wallet crypto2 key does not permit requested usages")
                }
            }
            val legacyKey = resolveLegacyKey(row)
            if (legacyKey != null || crypto2Key != null) WalletKeyStoreEntry(keyId, legacyKey, crypto2Key) else null
        }

    override suspend fun listKeys(): Flow<WalletKeyInfo> = suspendTransaction(db) {
        Wallet2Tables.Keys.selectAll()
            .where { Wallet2Tables.Keys.storeId eq storeId }
            .map { WalletKeyInfo(keyId = it[Wallet2Tables.Keys.keyId], keyType = it[Wallet2Tables.Keys.keyType]) }
    }.asFlow()

    /** Persists a crypto2 key as the canonical descriptor. No legacy representation is written. */
    override suspend fun addCrypto2Key(key: Crypto2Key): String {
        val stored = (key as? StorableKey)?.storedKey
            ?: throw IllegalArgumentException("Wallet key persistence requires a storable crypto2 key")
        require(key.id == stored.id && key.spec == stored.spec && key.usages == stored.usages) {
            "Key properties do not match the stored descriptor"
        }
        upsertKey(
            keyId = stored.id.value,
            keyType = stored.spec.toString(),
            serializedKey = null,
            crypto2StoredKey = StoredKeyCodec.encodeToString(stored),
        )
        return stored.id.value
    }

    /**
     * Persists a legacy key. This is the migration entry point: the descriptor is derived here so the
     * record is canonical from the start, and the legacy representation is retained alongside it for
     * consumers that still require a v1 [Key].
     */
    override suspend fun addKey(key: Key): String {
        val keyId = key.getKeyId()
        val serializedKey = KeySerialization.serializeKey(key)
        upsertKey(
            keyId = keyId,
            keyType = key.keyType.name,
            serializedKey = serializedKey,
            crypto2StoredKey = migrateSerializedKey(keyId, serializedKey)?.encoded,
        )
        return keyId
    }

    override suspend fun removeKey(keyId: String): Boolean =
        suspendTransaction(db) {
            Wallet2Tables.Keys.deleteWhere {
                (Wallet2Tables.Keys.storeId eq storeId) and (Wallet2Tables.Keys.keyId eq keyId)
            } > 0
        }

    private fun selectKey(keyId: String): ResultRow? =
        Wallet2Tables.Keys.selectAll()
            .where { (Wallet2Tables.Keys.storeId eq storeId) and (Wallet2Tables.Keys.keyId eq keyId) }
            .firstOrNull()

    private suspend fun upsertKey(
        keyId: String,
        keyType: String,
        serializedKey: String?,
        crypto2StoredKey: String?,
    ) {
        suspendTransaction(db) {
            Wallet2Tables.Keys.upsert {
                it[Wallet2Tables.Keys.storeId] = this@ExposedKeyStore.storeId
                it[Wallet2Tables.Keys.keyId] = keyId
                it[Wallet2Tables.Keys.keyType] = keyType
                it[Wallet2Tables.Keys.serializedKey] = serializedKey
                it[Wallet2Tables.Keys.crypto2StoredKey] = crypto2StoredKey
            }
        }
    }

    /**
     * Restores the canonical descriptor, migrating a pre-crypto2 row exactly once on first read.
     * A present but undecodable descriptor throws: the record is corrupt, and falling back to the
     * legacy representation would silently resurrect a key the descriptor no longer describes.
     */
    private suspend fun resolveCrypto2Key(row: ResultRow): Crypto2Key? {
        row[Wallet2Tables.Keys.crypto2StoredKey]?.let {
            return cryptoRuntime.restore(StoredKeyCodec.decodeFromString(it))
        }
        val keyId = row[Wallet2Tables.Keys.keyId]
        val serializedKey = row[Wallet2Tables.Keys.serializedKey] ?: return null
        val migrated = migrateSerializedKey(keyId, serializedKey) ?: return null
        check(adoptMigratedDescriptor(keyId, serializedKey, migrated.encoded) == 1) {
            "Wallet key changed while its crypto2 descriptor was being migrated"
        }
        return migrated.key
    }

    private suspend fun resolveLegacyKey(row: ResultRow): Key? {
        val serializedKey = row[Wallet2Tables.Keys.serializedKey] ?: return null
        return try {
            KeyManager.resolveSerializedKey(serializedKey)
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun migrateSerializedKey(keyId: String, serializedKey: String): MigratedKey? {
        val stored = migrateSerializedDescriptor(keyId, serializedKey) ?: return null
        return try {
            MigratedKey(
                encoded = StoredKeyCodec.encodeToString(stored),
                key = cryptoRuntime.restore(stored),
            )
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun migrateSerializedDescriptor(keyId: String, serializedKey: String): StoredKey? {
        val serialized = runCatching { Json.parseToJsonElement(serializedKey).jsonObject }.getOrNull()
            ?: return null
        if (serialized["type"]?.jsonPrimitive?.content != "jwk") return null
        val jwk = serialized["jwk"] as? JsonObject ?: return null
        val privateMaterial = listOf("d", "p", "q", "dp", "dq", "qi", "oth", "k").any(jwk::containsKey)
        val usages = if (privateMaterial) setOf(KeyUsage.SIGN, KeyUsage.VERIFY) else setOf(KeyUsage.VERIFY)
        return migration.migrate(KeyId(keyId), serialized, usages)
    }

    /** Compare-and-set so a concurrent writer is detected rather than overwritten. */
    private fun adoptMigratedDescriptor(keyId: String, serializedKey: String, encoded: String): Int =
        Wallet2Tables.Keys.update({
            (Wallet2Tables.Keys.storeId eq storeId) and
                    (Wallet2Tables.Keys.keyId eq keyId) and
                    (Wallet2Tables.Keys.serializedKey eq serializedKey) and
                    Wallet2Tables.Keys.crypto2StoredKey.isNull()
        }) {
            it[Wallet2Tables.Keys.crypto2StoredKey] = encoded
        }

    private data class MigratedKey(
        val encoded: String,
        val key: Crypto2Key,
    )
}
