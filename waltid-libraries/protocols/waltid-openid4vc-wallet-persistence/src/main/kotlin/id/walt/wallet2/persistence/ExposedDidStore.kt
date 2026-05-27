package id.walt.wallet2.persistence

import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.data.WalletDidStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction

/**
 * Exposed-backed [WalletDidStore].
 *
 * DID documents are stored as JSON text and deserialized back to [JsonObject] on load.
 */
class ExposedDidStore(
    val storeId: String,
    private val db: Database,
) : WalletDidStore {

    override suspend fun getDid(did: String): WalletDidEntry? =
        newSuspendedTransaction(db = db) {
            Wallet2Tables.Dids.selectAll()
                .where { (Wallet2Tables.Dids.storeId eq storeId) and (Wallet2Tables.Dids.did eq did) }
                .firstOrNull()
                ?.let { rowToEntry(it) }
        }

    override fun listDids(): Flow<WalletDidEntry> = flow {
        val rows = newSuspendedTransaction(db = db) {
            Wallet2Tables.Dids.selectAll()
                .where { Wallet2Tables.Dids.storeId eq storeId }
                .mapNotNull { rowToEntry(it) }
        }
        rows.forEach { emit(it) }
    }

    override suspend fun addDid(entry: WalletDidEntry) {
        newSuspendedTransaction(db = db) {
            Wallet2Tables.Dids.upsert {
                it[Wallet2Tables.Dids.storeId] = this@ExposedDidStore.storeId
                it[Wallet2Tables.Dids.did] = entry.did
                it[Wallet2Tables.Dids.document] = entry.document.toString()
            }
        }
    }

    override suspend fun removeDid(did: String): Boolean =
        newSuspendedTransaction(db = db) {
            Wallet2Tables.Dids.deleteWhere {
                (Wallet2Tables.Dids.storeId eq storeId) and (Wallet2Tables.Dids.did eq did)
            } > 0
        }

    private fun rowToEntry(row: ResultRow): WalletDidEntry? =
        runCatching {
            WalletDidEntry(
                did = row[Wallet2Tables.Dids.did],
                document = Json.parseToJsonElement(row[Wallet2Tables.Dids.document]) as JsonObject
            )
        }.getOrNull()
}
