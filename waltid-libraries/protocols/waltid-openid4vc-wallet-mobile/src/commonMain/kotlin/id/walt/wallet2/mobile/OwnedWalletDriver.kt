package id.walt.wallet2.mobile

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement

/** Wallet lifecycle admission serializes close and deletion. Only an explicit deletion may reopen the driver. */
internal class OwnedWalletDriver(initial: SqlDriver, private val open: suspend () -> SqlDriver) : SqlDriver {
    private var connection: SqlDriver? = initial
    private val driver: SqlDriver get() = checkNotNull(connection) { "Wallet database is closed" }

    suspend fun openForDeletion() {
        if (connection == null) connection = open()
    }

    override fun close() {
        connection?.close()
        connection = null
    }

    override fun <R> executeQuery(identifier: Int?, sql: String, mapper: (SqlCursor) -> QueryResult<R>,
                                  parameters: Int, binders: (SqlPreparedStatement.() -> Unit)?) =
        driver.executeQuery(identifier, sql, mapper, parameters, binders)

    override fun execute(identifier: Int?, sql: String, parameters: Int, binders: (SqlPreparedStatement.() -> Unit)?) =
        driver.execute(identifier, sql, parameters, binders)

    override fun newTransaction() = driver.newTransaction()
    override fun currentTransaction() = driver.currentTransaction()
    override fun addListener(vararg queryKeys: String, listener: Query.Listener) = driver.addListener(*queryKeys, listener = listener)
    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) = driver.removeListener(*queryKeys, listener = listener)
    override fun notifyListeners(vararg queryKeys: String) = driver.notifyListeners(*queryKeys)
}
