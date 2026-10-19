package id.walt.wallet2.data

import id.walt.wallet2.transactionlog.Transaction
import id.walt.wallet2.transactionlog.TransactionLogPage
import id.walt.wallet2.transactionlog.TransactionLogQuery

/** Storage for one wallet's transaction history. Use a separate store instance per wallet. */
interface WalletTransactionLogStore {
    /** Appends [entry]. Duplicate transaction identifiers must fail without overwriting history. */
    suspend fun store(entry: Transaction)

    /** Reads a time-ordered page. Invalid queries and corrupt stored entries must fail explicitly. */
    suspend fun read(query: TransactionLogQuery = TransactionLogQuery()): TransactionLogPage

    /** Removes a single entry by its transaction identifier. Returns true if it existed. */
    suspend fun remove(transactionIdentifier: String): Boolean

    /** Removes all history without affecting other wallet material. */
    suspend fun clear()
}
