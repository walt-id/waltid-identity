package id.walt.wallet2.persistence.stores

import id.walt.wallet2.data.WalletTransactionLogStore
import id.walt.wallet2.persistence.db.WalletPersistenceQueries
import id.walt.wallet2.transactionlog.TransactionLog
import id.walt.wallet2.transactionlog.TransactionLogCursor
import id.walt.wallet2.transactionlog.TransactionLogPage
import id.walt.wallet2.transactionlog.TransactionLogQuery
import id.walt.wallet2.transactionlog.TransactionLogSortField
import id.walt.wallet2.transactionlog.TransactionLogSortOrder
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json

/** SQLCipher-backed transaction history, with sorting and pagination performed in SQL. */
public class SqlDelightTransactionLogStore(private val queries: WalletPersistenceQueries) : WalletTransactionLogStore {
    /** Appends an envelope without overwriting an existing transaction identifier. */
    override suspend fun store(entry: TransactionLog) {
        queries.insertTransactionLogEntry(
            transaction_identifier = entry.transactionIdentifier,
            time = LocalDateTime.Formats.ISO.format(entry.time),
            payload = Json.encodeToString(TransactionLog.serializer(), entry),
        )
    }

    /** Reads a bounded page; malformed persisted envelopes fail rather than silently losing history. */
    override suspend fun read(query: TransactionLogQuery): TransactionLogPage {
        val cursor = query.cursor
        val cursorTime = cursor?.let { LocalDateTime.Formats.ISO.format(LocalDateTime.parse(it.sortValue)) }
        val fetchLimit = query.limit.toLong() + 1
        val payloads = when (query.sortBy) {
            TransactionLogSortField.Time -> when (query.sortOrder) {
                TransactionLogSortOrder.Ascending ->
                    queries.selectTransactionLogOldestFirst(cursorTime, cursor?.transactionIdentifier, fetchLimit)
                TransactionLogSortOrder.Descending ->
                    queries.selectTransactionLogNewestFirst(cursorTime, cursor?.transactionIdentifier, fetchLimit)
            }
        }.executeAsList()
        val entries = payloads.take(query.limit).map { Json.decodeFromString(TransactionLog.serializer(), it) }
        return TransactionLogPage(
            entries = entries,
            nextCursor = if (payloads.size > query.limit) entries.last().let {
                TransactionLogCursor(
                    sortValue = when (query.sortBy) {
                        TransactionLogSortField.Time -> LocalDateTime.Formats.ISO.format(it.time)
                    },
                    transactionIdentifier = it.transactionIdentifier,
                    sortBy = query.sortBy,
                    sortOrder = query.sortOrder,
                )
            } else null,
        )
    }

    /** Removes one entry and atomically reports whether it existed, without decoding its payload. */
    override suspend fun remove(transactionIdentifier: String): Boolean = queries.transactionWithResult {
        queries.deleteTransactionLogEntryById(transactionIdentifier)
        queries.transactionLogChanges().executeAsOne() == 1L
    }

    /** Removes transaction history without changing other wallet tables. */
    override suspend fun clear() {
        queries.clearTransactionLog()
    }
}
