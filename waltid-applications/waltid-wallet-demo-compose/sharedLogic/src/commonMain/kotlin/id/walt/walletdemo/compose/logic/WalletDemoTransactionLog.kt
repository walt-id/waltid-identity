package id.walt.walletdemo.compose.logic

//TODO - in future, add items for other types of entries
/** The transaction log entry categories the demo view can display and filter by. */
enum class WalletDemoTransactionLogType {
    Presentation,
    Issuance,
    Deletion,
}

/** Filter applied to the transaction log list; [All] shows every entry type. */
enum class WalletDemoTransactionLogFilter {
    All,
    Presentation,
    Issuance,
    Deletion;

    fun matches(type: WalletDemoTransactionLogType): Boolean = when (this) {
        All -> true
        Presentation -> type == WalletDemoTransactionLogType.Presentation
        Issuance -> type == WalletDemoTransactionLogType.Issuance
        Deletion -> type == WalletDemoTransactionLogType.Deletion
    }
}

/** Whether a logged transaction finished successfully. */
enum class WalletDemoTransactionLogResult {
    Completed,
    NotCompleted,
}

/** A single label/value line shown when an entry is expanded. */
data class WalletDemoTransactionLogDetail(
    val label: String,
    val value: String,
)

/**
 * A platform-agnostic projection of one transaction log entry for the demo UI.
 *
 * [transactionIdentifier] is stable and used for list keys and expansion state.
 * [relyingPartyName] is the counterparty shown in the summary (service provider, issuer, or
 * issuer snapshot for deletions); null when the log did not capture a name.
 */
data class WalletDemoTransactionLogEntry(
    val transactionIdentifier: String,
    val type: WalletDemoTransactionLogType,
    val time: String,
    val result: WalletDemoTransactionLogResult,
    val relyingPartyName: String? = null,
    val details: List<WalletDemoTransactionLogDetail> = emptyList(),
)

/**
 * A page of transaction log entries.
 *
 * [nextCursor] is an opaque continuation token to pass back for keyset pagination; null when no
 * further entries were available at read time.
 */
data class WalletDemoTransactionLogPage(
    val entries: List<WalletDemoTransactionLogEntry>,
    val nextCursor: String? = null,
)

/**
 * UI state for the read-only transaction log view.
 *
 * [entries] holds the raw, time-ordered entries accumulated across loaded pages; the [filter] is
 * applied in the UI layer so switching it never requires a refetch. [nextCursor] drives keyset
 * "load more" over the unfiltered stream.
 */
data class WalletDemoTransactionLogState(
    val supported: Boolean = false,
    val entries: List<WalletDemoTransactionLogEntry> = emptyList(),
    val nextCursor: String? = null,
    val filter: WalletDemoTransactionLogFilter = WalletDemoTransactionLogFilter.All,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val expandedIds: Set<String> = emptySet(),
) {
    val visibleEntries: List<WalletDemoTransactionLogEntry>
        get() = entries.filter { filter.matches(it.type) }

    val canLoadMore: Boolean
        get() = nextCursor != null && !isLoading && !isLoadingMore
}

