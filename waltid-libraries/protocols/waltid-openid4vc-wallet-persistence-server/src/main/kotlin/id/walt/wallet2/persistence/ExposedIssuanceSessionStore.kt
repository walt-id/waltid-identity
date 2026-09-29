package id.walt.wallet2.persistence

import id.walt.wallet2.handlers.WalletIssuanceSessionRecord
import id.walt.wallet2.handlers.WalletIssuanceSessionRecordKind
import id.walt.wallet2.handlers.WalletIssuanceSessionStore
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert
import org.jetbrains.exposed.v1.jdbc.update

/**
 * Continuations scoped to one wallet in its configured database.
 * The database must provide the same confidentiality and integrity boundary as wallet private keys.
 * Payloads contain bearer tokens and must never be exposed through named-store or credential APIs.
 */
class ExposedIssuanceSessionStore(private val scopedWalletId: String, private val db: Database) : WalletIssuanceSessionStore {
    private val table = Wallet2Tables.IssuanceSessions

    override suspend fun get(id: String): WalletIssuanceSessionRecord? = suspendTransaction(db) {
        table.selectAll().where { (table.walletId eq scopedWalletId) and (table.id eq id) }.singleOrNull()?.toRecord()
    }

    override suspend fun list(): List<WalletIssuanceSessionRecord> = suspendTransaction(db) {
        table.selectAll().where { table.walletId eq scopedWalletId }.map { it.toRecord() }
    }

    override suspend fun put(record: WalletIssuanceSessionRecord) {
        suspendTransaction(db) {
            // SQLite deployments need not enable foreign-key enforcement. Keep this read in the
            // serializable write transaction so deletion cannot leave a new orphaned continuation.
            check(Wallet2Tables.Wallets.selectAll().where { Wallet2Tables.Wallets.id eq scopedWalletId }.any()) {
                "Wallet no longer exists"
            }
            table.upsert {
                it[table.walletId] = scopedWalletId
                it[table.id] = record.id
                it[table.sessionId] = record.sessionId
                it[table.kind] = record.kind.name
                it[table.payload] = record.payload
                it[table.updatedAt] = record.updatedAtEpochMilliseconds
            }
        }
    }

    override suspend fun compareAndSet(expected: WalletIssuanceSessionRecord, replacement: WalletIssuanceSessionRecord?): Boolean {
        require(replacement == null || expected.id == replacement.id) { "Cannot change a continuation record ID" }
        return suspendTransaction(db) {
            val condition =
                (table.walletId eq scopedWalletId) and (table.id eq expected.id) and
                    (table.sessionId eq expected.sessionId) and (table.kind eq expected.kind.name) and
                    (table.payload eq expected.payload) and (table.updatedAt eq expected.updatedAtEpochMilliseconds)
            if (replacement == null) table.deleteWhere { condition } == 1
            else table.update({ condition }) {
                it[table.sessionId] = replacement.sessionId
                it[table.kind] = replacement.kind.name
                it[table.payload] = replacement.payload
                it[table.updatedAt] = replacement.updatedAtEpochMilliseconds
            } == 1
        }
    }

    override suspend fun remove(id: String): Boolean {
        val recordId = id
        return suspendTransaction(db) {
            table.deleteWhere { (table.walletId eq scopedWalletId) and (table.id eq recordId) } > 0
        }
    }

    private fun ResultRow.toRecord() = WalletIssuanceSessionRecord(
        id = this[table.id], sessionId = this[table.sessionId], kind = WalletIssuanceSessionRecordKind.valueOf(this[table.kind]),
        payload = this[table.payload], updatedAtEpochMilliseconds = this[table.updatedAt],
    )
}
