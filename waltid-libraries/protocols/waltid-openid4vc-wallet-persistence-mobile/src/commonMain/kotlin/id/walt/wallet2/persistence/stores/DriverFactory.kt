package id.walt.wallet2.persistence.stores

import app.cash.sqldelight.db.SqlDriver

/**
 * Platform factory for SQLDelight drivers used by the mobile wallet database.
 */
expect class DriverFactory {
    /**
     * Creates a SQLDelight driver for the named wallet database.
     *
     * @param databaseName Base database name. Platform implementations add the expected file extension.
     */
    fun createDriver(databaseName: String): SqlDriver
}
