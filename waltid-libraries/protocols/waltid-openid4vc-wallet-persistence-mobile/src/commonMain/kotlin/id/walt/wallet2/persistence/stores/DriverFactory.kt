package id.walt.wallet2.persistence.stores

import app.cash.sqldelight.db.SqlDriver
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey

/**
 * Platform factory for encrypted SQLDelight drivers used by the mobile wallet database.
 */
public expect class DriverFactory {
    /**
     * Creates an encrypted SQLDelight driver for the named wallet database.
     *
     * @param databaseName Base database name. Platform implementations add the expected file extension.
     * @param encryptionKey Raw database key used by SQLCipher.
     * @param isDeviceLocal Whether SDK-managed persistence should use device-local backup semantics.
     * @param walletId Wallet identifier used in typed persistence errors.
     */
    public fun createEncryptedDriver(
        databaseName: String,
        encryptionKey: DatabaseEncryptionKey,
        isDeviceLocal: Boolean = true,
        walletId: String = databaseName,
    ): SqlDriver

    /**
     * Deletes the database file and SQLite sidecar files for [databaseName].
     */
    public fun deleteDatabase(databaseName: String)
}
