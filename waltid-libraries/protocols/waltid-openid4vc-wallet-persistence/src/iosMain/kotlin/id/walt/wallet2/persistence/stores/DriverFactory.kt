package id.walt.wallet2.persistence.stores

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase

actual class DriverFactory {
    actual fun createDriver(databaseName: String): SqlDriver =
        NativeSqliteDriver(WalletPersistenceDatabase.Schema, "$databaseName.db")
}
