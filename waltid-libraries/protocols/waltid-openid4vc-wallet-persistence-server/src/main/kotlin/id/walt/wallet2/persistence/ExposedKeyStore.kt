package id.walt.wallet2.persistence

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeySerialization
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.data.WalletKeyStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
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

    override suspend fun getKey(keyId: String): Key? =
        suspendTransaction(db) {
            Wallet2Tables.Keys.selectAll()
                .where { (Wallet2Tables.Keys.storeId eq storeId) and (Wallet2Tables.Keys.keyId eq keyId) }
                .firstOrNull()
                ?.let { row ->
                    runCatching {
                        KeyManager.resolveSerializedKey(row[Wallet2Tables.Keys.serializedKey])
                    }.getOrNull()
                }
        }

    override suspend fun listKeys(): Flow<WalletKeyInfo> = suspendTransaction(db) {
        Wallet2Tables.Keys.selectAll()
            .where { Wallet2Tables.Keys.storeId eq storeId }
            .map { WalletKeyInfo(keyId = it[Wallet2Tables.Keys.keyId], keyType = it[Wallet2Tables.Keys.keyType]) }
    }.asFlow()

    override suspend fun addKey(key: Key): String {
        val keyId = key.getKeyId()
        suspendTransaction(db) {
            Wallet2Tables.Keys.upsert {
                it[Wallet2Tables.Keys.storeId] = this@ExposedKeyStore.storeId
                it[Wallet2Tables.Keys.keyId] = keyId
                it[Wallet2Tables.Keys.keyType] = key.keyType.name
                it[Wallet2Tables.Keys.serializedKey] = KeySerialization.serializeKey(key)
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
}
