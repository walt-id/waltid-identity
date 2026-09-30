package id.walt.wallet2.persistence.stores

import id.walt.wallet2.handlers.WalletIssuanceSessionRecord
import id.walt.wallet2.handlers.WalletIssuanceSessionRecordKind
import id.walt.wallet2.handlers.AtomicWalletIssuanceSessionStore
import id.walt.wallet2.persistence.db.WalletPersistenceQueries

/** SQLCipher-backed storage for sensitive OpenID4VCI continuation records. */
public class SqlDelightIssuanceSessionStore(
    private val queries: WalletPersistenceQueries,
) : AtomicWalletIssuanceSessionStore {
    /** Returns the encrypted continuation record identified by [id], when present. */
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

    /** Returns all encrypted continuation records currently retained by the wallet. */
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

    /** Inserts or replaces an encrypted continuation [record]. */
    override suspend fun put(record: WalletIssuanceSessionRecord) {
        queries.insertIssuanceSessionRecord(
            record_id = record.id,
            session_id = record.sessionId,
            kind = record.kind.name,
            payload = record.payload,
            updated_at = record.updatedAtEpochMilliseconds,
        )
    }

    /** Claims or checkpoints a continuation only when the persisted snapshot is unchanged. */
    override suspend fun compareAndSet(expected: WalletIssuanceSessionRecord, replacement: WalletIssuanceSessionRecord?): Boolean {
        require(replacement == null || expected.id == replacement.id) { "Cannot change a continuation record ID" }
        return queries.transactionWithResult {
            if (replacement == null) queries.compareAndRemoveIssuanceSessionRecord(
                record_id = expected.id,
                expected_session_id = expected.sessionId,
                expected_kind = expected.kind.name,
                expected_payload = expected.payload,
                expected_updated_at = expected.updatedAtEpochMilliseconds,
            ) else queries.compareAndSetIssuanceSessionRecord(
                replacement_session_id = replacement.sessionId,
                replacement_kind = replacement.kind.name,
                replacement_payload = replacement.payload,
                replacement_updated_at = replacement.updatedAtEpochMilliseconds,
                record_id = expected.id,
                expected_session_id = expected.sessionId,
                expected_kind = expected.kind.name,
                expected_payload = expected.payload,
                expected_updated_at = expected.updatedAtEpochMilliseconds,
            )
            queries.issuanceSessionRecordChanges().executeAsOne() == 1L
        }
    }

    /** Removes the record identified by [id] and reports whether it existed. */
    override suspend fun remove(id: String): Boolean {
        val exists = queries.selectIssuanceSessionRecordById(id).executeAsOneOrNull() != null
        if (exists) queries.deleteIssuanceSessionRecordById(id)
        return exists
    }
}
