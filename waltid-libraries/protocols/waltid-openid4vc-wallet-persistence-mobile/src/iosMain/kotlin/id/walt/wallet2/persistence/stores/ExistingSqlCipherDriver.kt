@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package id.walt.wallet2.persistence.stores

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import cnames.structs.sqlite3
import cnames.structs.sqlite3_stmt
import co.touchlab.sqliter.sqlite3.*
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import kotlinx.cinterop.*
import platform.Foundation.NSRecursiveLock

/**
 * One serialized SQLCipher handle for generated SQLDelight queries. SQLiter's native manager
 * always uses OPEN_CREATE. This short-lived driver has no schema or observation lifecycle.
 */
internal class ExistingSqlCipherDriver private constructor(private val database: CPointer<sqlite3>) : SqlDriver {
    private val lock = NSRecursiveLock()
    private var activeTransaction: Transaction? = null

    private inline fun <T> locked(block: () -> T): T {
        lock.lock()
        return try { block() } finally { lock.unlock() }
    }

    private fun <T> statement(sql: String, binders: (SqlPreparedStatement.() -> Unit)?, run: (CPointer<sqlite3_stmt>) -> T): T =
        locked {
            memScoped {
                val output = alloc<CPointerVar<sqlite3_stmt>>()
                checkSqlite(sqlite3_prepare_v2(database, sql.cstr.ptr, -1, output.ptr, null))
                val handle = checkNotNull(output.value)
                try {
                    if (binders != null) Bindings(handle).binders()
                    run(handle)
                } finally {
                    sqlite3_finalize(handle)
                }
            }
        }

    override fun execute(
        identifier: Int?, sql: String, parameters: Int, binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> = statement(sql, binders) {
        checkSqlite(sqlite3_step(it), SQLITE_DONE)
        QueryResult.Value(sqlite3_changes(database).toLong())
    }

    override fun <R> executeQuery(
        identifier: Int?, sql: String, mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int, binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> = statement(sql, binders) { mapper(Cursor(it)) }

    override fun currentTransaction(): Transacter.Transaction? = locked { activeTransaction }

    override fun newTransaction(): QueryResult<Transacter.Transaction> {
        lock.lock()
        try {
            val enclosing = activeTransaction
            if (enclosing == null) execute(null, "BEGIN IMMEDIATE", 0, null)
            return QueryResult.Value(Transaction(enclosing).also { activeTransaction = it })
        } catch (cause: Throwable) {
            lock.unlock()
            throw cause
        }
    }

    private inner class Transaction(override val enclosingTransaction: Transacter.Transaction?) : Transacter.Transaction() {
        override fun endTransaction(successful: Boolean): QueryResult<Unit> {
            try {
                if (enclosingTransaction == null) {
                    try {
                        execute(null, if (successful) "COMMIT" else "ROLLBACK", 0, null)
                    } catch (failure: StatusMaintenanceStorageException) {
                        // A busy COMMIT leaves SQLite's transaction open.
                        if (sqlite3_get_autocommit(database) == 0) {
                            try { execute(null, "ROLLBACK", 0, null) }
                            catch (rollback: StatusMaintenanceStorageException) { failure.addSuppressed(rollback) }
                        }
                        throw failure
                    }
                }
                return QueryResult.Value(Unit)
            } finally {
                activeTransaction = enclosingTransaction as Transaction?
                lock.unlock()
            }
        }
    }

    override fun addListener(vararg queryKeys: String, listener: Query.Listener) = Unit
    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) = Unit
    override fun notifyListeners(vararg queryKeys: String) = Unit
    override fun close() = locked { checkSqlite(sqlite3_close(database)) }

    private class Cursor(private val statement: CPointer<sqlite3_stmt>) : SqlCursor {
        override fun next(): QueryResult<Boolean> = QueryResult.Value(when (val result = sqlite3_step(statement)) {
            SQLITE_ROW -> true
            SQLITE_DONE -> false
            else -> throw StatusMaintenanceStorageException(result)
        })

        private fun isNull(index: Int) = sqlite3_column_type(statement, index) == SQLITE_NULL
        override fun getString(index: Int): String? = sqlite3_column_text(statement, index)
            ?.readBytes(sqlite3_column_bytes(statement, index))?.decodeToString()
        override fun getLong(index: Int): Long? = if (isNull(index)) null else sqlite3_column_int64(statement, index)
        override fun getDouble(index: Int): Double? = if (isNull(index)) null else sqlite3_column_double(statement, index)
        override fun getBoolean(index: Int): Boolean? = getLong(index)?.let { it != 0L }
        override fun getBytes(index: Int): ByteArray? = if (isNull(index)) null else {
            sqlite3_column_blob(statement, index)?.readBytes(sqlite3_column_bytes(statement, index)) ?: ByteArray(0)
        }
    }

    private class Bindings(private val statement: CPointer<sqlite3_stmt>) : SqlPreparedStatement {
        private fun bind(index: Int, binder: (Int) -> Int) = checkSqlite(binder(index + 1))
        override fun bindLong(index: Int, long: Long?) = bind(index) {
            if (long == null) sqlite3_bind_null(statement, it) else sqlite3_bind_int64(statement, it, long)
        }
        override fun bindDouble(index: Int, double: Double?) = bind(index) {
            if (double == null) sqlite3_bind_null(statement, it) else sqlite3_bind_double(statement, it, double)
        }
        override fun bindBoolean(index: Int, boolean: Boolean?) = bindLong(index, boolean?.let { if (it) 1L else 0L })
        override fun bindString(index: Int, string: String?) = bind(index) {
            if (string == null) sqlite3_bind_null(statement, it)
            else sqlite3_bind_text(statement, it, string, string.encodeToByteArray().size, SQLITE_TRANSIENT)
        }
        override fun bindBytes(index: Int, bytes: ByteArray?) = bind(index) { position ->
            when {
                bytes == null -> sqlite3_bind_null(statement, position)
                bytes.isEmpty() -> sqlite3_bind_zeroblob(statement, position, 0)
                else -> bytes.usePinned { sqlite3_bind_blob(statement, position, it.addressOf(0), bytes.size, SQLITE_TRANSIENT) }
            }
        }
    }

    companion object {
        fun open(path: String, passphrase: String): SqlDriver? = memScoped {
            val output = alloc<CPointerVar<sqlite3>>()
            val result = sqlite3_open_v2(path, output.ptr, SQLITE_OPEN_READWRITE or SQLITE_OPEN_FULLMUTEX, null)
            val handle = output.value
            if (result != SQLITE_OK) {
                sqlite3_close(handle)
                throw StatusMaintenanceStorageException(result)
            }
            val driver = ExistingSqlCipherDriver(checkNotNull(handle))
            try {
                if (sqlite3_db_readonly(handle, "main") != 0) throw StatusMaintenanceStorageException(SQLITE_READONLY)
                checkSqlite(sqlite3_busy_timeout(handle, 250))
                // Hex-encoded SDK key, using the same passphrase contract as the foreground SQLiter driver.
                checkSqlite(sqlite3_exec(handle, "PRAGMA key = '$passphrase'", null, null, null))
                val cipher = driver.executeQuery(null, "PRAGMA cipher_version", { cursor ->
                    QueryResult.Value(cursor.next().value && !cursor.getString(0).isNullOrBlank())
                }, 0, null).value
                val version = driver.executeQuery(null, "PRAGMA user_version", { cursor ->
                    QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
                }, 0, null).value
                if (cipher && version == WalletPersistenceDatabase.Schema.version) return@memScoped driver
            } catch (cause: Throwable) {
                driver.close()
                throw cause
            }
            driver.close()
            null
        }
    }
}

private fun checkSqlite(result: Int, expected: Int = SQLITE_OK) {
    if (result != expected) throw StatusMaintenanceStorageException(result)
}
