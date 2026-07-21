package id.walt.wallet2.persistence.stores

import id.walt.wallet2.handlers.WalletIssuanceSessionRecord
import id.walt.wallet2.handlers.WalletIssuanceSessionRecordKind
import id.walt.wallet2.handlers.WalletIssuanceSessionStore
import id.walt.wallet2.persistence.db.WalletPersistenceQueries

/** SQLCipher-backed storage for sensitive OpenID4VCI continuation records. */
public class SqlDelightIssuanceSessionStore(
    private val queries: WalletPersistenceQueries,
) : WalletIssuanceSessionStore {
    override suspend fun get(id: String): WalletIssuanceSessionRecord? =
        queries.selectIssuanceSessionRecordById(id).executeAsOneOrNull()?.let { row ->
            WalletIssuanceSessionRecord(
                id = row.record_id,
                sessionId = row.session_id,
                kind = WalletIssuanceSessionRecordKind.valueOf(row.kind),
                payload = row.payload,
                updatedAtEpochMilliseconds = row.updated_at,
            )
        }

    override suspend fun list(): List<WalletIssuanceSessionRecord> =
        queries.selectAllIssuanceSessionRecords().executeAsList().map { row ->
            WalletIssuanceSessionRecord(
                id = row.record_id,
                sessionId = row.session_id,
                kind = WalletIssuanceSessionRecordKind.valueOf(row.kind),
                payload = row.payload,
                updatedAtEpochMilliseconds = row.updated_at,
            )
        }

    override suspend fun put(record: WalletIssuanceSessionRecord) {
        queries.insertIssuanceSessionRecord(
            record_id = record.id,
            session_id = record.sessionId,
            kind = record.kind.name,
            payload = record.payload,
            updated_at = record.updatedAtEpochMilliseconds,
        )
    }

    override suspend fun remove(id: String): Boolean {
        val exists = queries.selectIssuanceSessionRecordById(id).executeAsOneOrNull() != null
        if (exists) queries.deleteIssuanceSessionRecordById(id)
        return exists
    }
}
