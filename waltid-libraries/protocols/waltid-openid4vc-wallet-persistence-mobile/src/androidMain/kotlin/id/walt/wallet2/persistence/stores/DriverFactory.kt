package id.walt.wallet2.persistence.stores

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase

/**
 * Android SQLDelight driver factory for app-private SQLite wallet databases.
 *
 * @param context Android context used by [AndroidSqliteDriver].
 */
public actual class DriverFactory(private val context: Context) {
    /**
     * Creates an Android SQLite driver for [databaseName].
     */
    public actual fun createDriver(databaseName: String): SqlDriver =
        AndroidSqliteDriver(WalletPersistenceDatabase.Schema, context, "$databaseName.db")
}
