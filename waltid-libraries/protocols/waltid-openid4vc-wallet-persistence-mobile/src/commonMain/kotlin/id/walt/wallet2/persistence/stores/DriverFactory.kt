package id.walt.wallet2.persistence.stores

import app.cash.sqldelight.db.SqlDriver

expect class DriverFactory {
    fun createDriver(databaseName: String): SqlDriver
}
