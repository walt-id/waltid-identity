package id.walt.wallet2.persistence

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.crypto2.migration.v1.V1KeyMigration
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.data.WalletKeyStoreEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert

/**
 * Exposed-backed [WalletKeyStore].
 *
 * Keys are serialized with [KeySerialization.serializeKey] and deserialized with
 * [KeyManager.resolveSerializedKey] — full round-trip, no information loss.
 */
class ExposedKeyStore(
    val storeId: String,
    private val db: Database,
) : WalletKeyStore {
    private val crypto2Runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))
    private val migration = V1KeyMigration()

    override suspend fun getKey(keyId: String): Key? =
        suspendTransaction(db) {
            Wallet2Tables.Keys.selectAll()
                .where { (Wallet2Tables.Keys.storeId eq storeId) and (Wallet2Tables.Keys.keyId eq keyId) }
                .firstOrNull()
                ?.let { row ->
                    resolveCrypto2Key(row)
                    resolveLegacyKey(row)
                }
        }

    override suspend fun getCrypto2Key(keyId: String, usages: Set<KeyUsage>): Crypto2Key? = suspendTransaction(db) {
        val row = Wallet2Tables.Keys.selectAll()
            .where { (Wallet2Tables.Keys.storeId eq storeId) and (Wallet2Tables.Keys.keyId eq keyId) }
            .firstOrNull() ?: return@suspendTransaction null
        resolveCrypto2Key(row)?.also { key ->
            require(usages.all(key.usages::contains)) { "Wallet crypto2 key does not permit requested usages" }
        }
    }

    suspend fun getCrypto2Key(keyId: String): Crypto2Key? =
        getCrypto2Key(keyId, emptySet())

    override suspend fun getKeyMaterial(keyId: String, usages: Set<KeyUsage>): WalletKeyStoreEntry? =
        suspendTransaction(db) {
            val row = Wallet2Tables.Keys.selectAll()
                .where { (Wallet2Tables.Keys.storeId eq storeId) and (Wallet2Tables.Keys.keyId eq keyId) }
                .firstOrNull() ?: return@suspendTransaction null
            val crypto2Key = resolveCrypto2Key(row)?.also { key ->
                require(usages.all(key.usages::contains)) { "Wallet crypto2 key does not permit requested usages" }
            }
            val legacyKey = resolveLegacyKey(row)
            if (legacyKey != null || crypto2Key != null) WalletKeyStoreEntry(keyId, legacyKey, crypto2Key) else null
        }

    override suspend fun listKeys(): Flow<WalletKeyInfo> = suspendTransaction(db) {
        Wallet2Tables.Keys.selectAll()
            .where { Wallet2Tables.Keys.storeId eq storeId }
            .map { WalletKeyInfo(keyId = it[Wallet2Tables.Keys.keyId], keyType = it[Wallet2Tables.Keys.keyType]) }
    }.asFlow()

    override suspend fun addKey(key: Key): String {
        val keyId = key.getKeyId()
        val serializedKey = KeySerialization.serializeKey(key)
        val crypto2StoredKey = migrateSerializedKey(keyId, serializedKey)?.encoded
        suspendTransaction(db) {
            Wallet2Tables.Keys.upsert {
                it[Wallet2Tables.Keys.storeId] = this@ExposedKeyStore.storeId
                it[Wallet2Tables.Keys.keyId] = keyId
                it[Wallet2Tables.Keys.keyType] = key.keyType.name
                it[Wallet2Tables.Keys.serializedKey] = serializedKey
                it[Wallet2Tables.Keys.crypto2StoredKey] = crypto2StoredKey
            }
        }
        return keyId
    }

    override suspend fun removeKey(keyId: String): Boolean =
        suspendTransaction(db) {
            Wallet2Tables.Keys.deleteWhere {
                (Wallet2Tables.Keys.storeId eq storeId) and (Wallet2Tables.Keys.keyId eq keyId)
            } > 0
        }

    private suspend fun resolveCrypto2Key(row: ResultRow): Crypto2Key? {
        val keyId = row[Wallet2Tables.Keys.keyId]
        val serializedKey = row[Wallet2Tables.Keys.serializedKey]
        val expected = migrateSerializedKey(keyId, serializedKey)
        val persisted = row[Wallet2Tables.Keys.crypto2StoredKey]
        if (persisted == null) {
            expected?.let {
                check(backfillCrypto2Key(keyId, serializedKey, null, it.encoded) == 1) {
                    "Wallet key changed while its crypto2 descriptor was being backfilled"
                }
            }
            return expected?.key
        }
        val persistedStoredKey = StoredKeyCodec.decodeFromString(persisted)
        val restored = crypto2Runtime.restore(persistedStoredKey)
        requireNotNull(expected) {
            "Persisted crypto2 key exists but the current legacy key cannot be migrated"
        }
        if (persistedStoredKey != StoredKeyCodec.decodeFromString(expected.encoded)) {
            check(backfillCrypto2Key(keyId, serializedKey, persisted, expected.encoded) == 1) {
                "Wallet key changed while its stale crypto2 descriptor was being replaced"
            }
            return expected.key
        }
        return restored
    }

    private suspend fun resolveLegacyKey(row: ResultRow): Key? = try {
        KeyManager.resolveSerializedKey(row[Wallet2Tables.Keys.serializedKey])
    } catch (cause: CancellationException) {
        throw cause
    } catch (_: Exception) {
        null
    }

    private suspend fun migrateSerializedKey(keyId: String, serializedKey: String): MigratedKey? {
        val serialized = runCatching { Json.parseToJsonElement(serializedKey).jsonObject }.getOrNull()
            ?: return null
        if (serialized["type"]?.jsonPrimitive?.content != "jwk") return null
        val jwk = serialized["jwk"] as? JsonObject ?: return null
        val privateMaterial = listOf("d", "p", "q", "dp", "dq", "qi", "oth", "k").any(jwk::containsKey)
        val usages = if (privateMaterial) setOf(KeyUsage.SIGN, KeyUsage.VERIFY) else setOf(KeyUsage.VERIFY)
        val stored = migration.migrate(KeyId(keyId), serialized, usages)
        return try {
            MigratedKey(
                encoded = StoredKeyCodec.encodeToString(stored),
                key = crypto2Runtime.restore(stored),
            )
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
            null
        }
    }

    private fun backfillCrypto2Key(
        keyId: String,
        serializedKey: String,
        currentCrypto2Key: String?,
        encoded: String,
    ): Int {
        val crypto2Condition = currentCrypto2Key?.let { Wallet2Tables.Keys.crypto2StoredKey eq it }
            ?: Wallet2Tables.Keys.crypto2StoredKey.isNull()
        return Wallet2Tables.Keys.update({
            (Wallet2Tables.Keys.storeId eq storeId) and
                (Wallet2Tables.Keys.keyId eq keyId) and
                (Wallet2Tables.Keys.serializedKey eq serializedKey) and
                crypto2Condition
        }) {
            it[Wallet2Tables.Keys.crypto2StoredKey] = encoded
        }
    }

    private data class MigratedKey(
        val encoded: String,
        val key: Crypto2Key,
    )
}
