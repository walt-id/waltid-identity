package id.walt.wallet2.stores.inmemory

import id.walt.wallet2.data.WalletTransactionLogStore
import id.walt.wallet2.transactionlog.TransactionLog
import id.walt.wallet2.transactionlog.TransactionLogCursor
import id.walt.wallet2.transactionlog.TransactionLogPage
import id.walt.wallet2.transactionlog.TransactionLogQuery
import id.walt.wallet2.transactionlog.TransactionLogSortField
import id.walt.wallet2.transactionlog.TransactionLogSortOrder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDateTime

/** Process-local transaction history for development and testing; not durable storage. */
class InMemoryTransactionLogStore : WalletTransactionLogStore {
    private val entries = mutableMapOf<String, TransactionLog>()
    private val mutex = Mutex()

    override suspend fun store(entry: TransactionLog) {
        mutex.withLock {
            check(entry.transactionIdentifier !in entries) { "Transaction identifier already exists: ${entry.transactionIdentifier}" }
            entries[entry.transactionIdentifier] = entry
        }
    }

    override suspend fun read(query: TransactionLogQuery): TransactionLogPage = mutex.withLock {
        val ascending = compareBy<TransactionLog> { it.sortValue(query.sortBy) }.thenBy { it.transactionIdentifier }
        val comparator = when (query.sortOrder) {
            TransactionLogSortOrder.Ascending -> ascending
            TransactionLogSortOrder.Descending -> ascending.reversed()
        }
        val cursor = query.cursor?.let {
            it.copy(sortValue = LocalDateTime.Formats.ISO.format(LocalDateTime.parse(it.sortValue)))
        }
        val remaining = entries.values.filter { entry ->
            if (cursor == null) true else {
                val comparison = entry.sortValue(query.sortBy).compareTo(cursor.sortValue).takeIf { it != 0 }
                    ?: entry.transactionIdentifier.compareTo(cursor.transactionIdentifier)
                when (query.sortOrder) {
                    TransactionLogSortOrder.Ascending -> comparison > 0
                    TransactionLogSortOrder.Descending -> comparison < 0
                }
            }
        }.sortedWith(comparator)
        val page = remaining.take(query.limit)
        TransactionLogPage(
            entries = page,
            nextCursor = if (remaining.size > query.limit) page.last().let {
                TransactionLogCursor(
                    sortValue = it.sortValue(query.sortBy),
                    transactionIdentifier = it.transactionIdentifier,
                    sortBy = query.sortBy,
                    sortOrder = query.sortOrder,
                )
            } else null,
        )
    }

    override suspend fun remove(transactionIdentifier: String): Boolean = mutex.withLock {
        entries.remove(transactionIdentifier) != null
    }

    override suspend fun clear() {
        mutex.withLock { entries.clear() }
    }

    private fun TransactionLog.sortValue(field: TransactionLogSortField): String = when (field) {
        TransactionLogSortField.Time -> LocalDateTime.Formats.ISO.format(time)
    }
}
