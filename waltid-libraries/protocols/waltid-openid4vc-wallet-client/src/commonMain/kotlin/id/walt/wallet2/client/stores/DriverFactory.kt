package id.walt.wallet2.client.stores

import app.cash.sqldelight.db.SqlDriver

expect class DriverFactory {
    fun createDriver(databaseName: String): SqlDriver
}
