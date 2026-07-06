package id.walt.wallet2.persistence.stores

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase

/**
 * iOS SQLDelight driver factory for native SQLite wallet databases.
 */
actual class DriverFactory {
    /**
     * Creates a native SQLite driver for [databaseName].
     */
    actual fun createDriver(databaseName: String): SqlDriver =
        NativeSqliteDriver(WalletPersistenceDatabase.Schema, "$databaseName.db")
}
