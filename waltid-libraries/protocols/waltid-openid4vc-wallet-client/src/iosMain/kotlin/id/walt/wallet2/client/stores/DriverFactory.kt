package id.walt.wallet2.client.stores

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import id.walt.wallet2.client.db.WalletClientDatabase

actual class DriverFactory {
    actual fun createDriver(databaseName: String): SqlDriver =
        NativeSqliteDriver(WalletClientDatabase.Schema, "$databaseName.db")
}
