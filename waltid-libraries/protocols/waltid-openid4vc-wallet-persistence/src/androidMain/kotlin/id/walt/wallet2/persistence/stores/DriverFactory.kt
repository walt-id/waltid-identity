package id.walt.wallet2.persistence.stores

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase

actual class DriverFactory(private val context: Context) {
    actual fun createDriver(databaseName: String): SqlDriver =
        AndroidSqliteDriver(WalletPersistenceDatabase.Schema, context, "$databaseName.db")
}
