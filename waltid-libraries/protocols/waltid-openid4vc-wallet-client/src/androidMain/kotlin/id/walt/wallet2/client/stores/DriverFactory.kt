package id.walt.wallet2.client.stores

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import id.walt.wallet2.client.db.WalletClientDatabase

actual class DriverFactory(private val context: Context) {
    actual fun createDriver(databaseName: String): SqlDriver =
        AndroidSqliteDriver(WalletClientDatabase.Schema, context, "$databaseName.db")
}
