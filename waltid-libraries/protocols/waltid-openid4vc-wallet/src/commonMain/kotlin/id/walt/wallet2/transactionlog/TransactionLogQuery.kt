package id.walt.wallet2.transactionlog

import kotlinx.datetime.LocalDateTime

/** Supported sort fields. Stores must handle each field explicitly, including cursor comparisons. */
enum class TransactionLogSortField {
    Time,
}

/** Sort direction, also applied to the transaction identifier used to break equal-value ties. */
enum class TransactionLogSortOrder {
    Ascending,
    Descending,
}

/**
 * Exclusive position in a transaction history. Reuse only with the same wallet, field and order.
 * The boundary entry need not still exist; this is a position, not a database row reference.
 * [sortValue] is the boundary entry's value for [sortBy], validated according to that field.
 */
data class TransactionLogCursor(
    val sortValue: String,
    val transactionIdentifier: String,
    val sortBy: TransactionLogSortField = TransactionLogSortField.Time,
    val sortOrder: TransactionLogSortOrder = TransactionLogSortOrder.Descending,
) {
    init {
        when (sortBy) {
            TransactionLogSortField.Time -> LocalDateTime.parse(sortValue)
        }
        require(transactionIdentifier.isNotBlank()) { "Cursor transaction identifier must not be blank" }
    }
}

/**
 * Bounded keyset query, defaulting to the 50 newest entries.
 *
 * Continue with [TransactionLogPage.nextCursor]. Insertion or deletion before the boundary does
 * not shift subsequent pages. This is not a snapshot: newly inserted entries after the boundary
 * may appear on later pages. Restart without a cursor to refresh earlier history.
 */
data class TransactionLogQuery(
    val limit: Int = 50,
    val sortBy: TransactionLogSortField = TransactionLogSortField.Time,
    val sortOrder: TransactionLogSortOrder = TransactionLogSortOrder.Descending,
    val cursor: TransactionLogCursor? = null,
) {
    init {
        require(limit > 0) { "Transaction log page limit must be positive" }
        require(cursor == null || cursor.sortBy == sortBy) { "Cursor sort field must match the query" }
        require(cursor == null || cursor.sortOrder == sortOrder) { "Cursor sort order must match the query" }
    }
}

/** A page of entries with a continuation only when more entries were available at read time. */
data class TransactionLogPage(
    val entries: List<TransactionLog>,
    val nextCursor: TransactionLogCursor?,
)
