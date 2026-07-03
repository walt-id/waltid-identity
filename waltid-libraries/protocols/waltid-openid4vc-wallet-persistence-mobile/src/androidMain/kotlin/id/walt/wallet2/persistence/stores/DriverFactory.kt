package id.walt.wallet2.persistence.stores

import android.content.Context
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.persistence.encryption.WalletPersistenceException
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Android SQLDelight driver factory for app-private SQLite wallet databases.
 *
 * @param context Android context used by [AndroidSqliteDriver].
 */
actual class DriverFactory(private val context: Context) {
    /**
     * Creates an Android SQLite driver for [databaseName].
     */
    actual fun createDriver(databaseName: String): SqlDriver =
        AndroidSqliteDriver(WalletPersistenceDatabase.Schema, context, "$databaseName.db")

    fun createEncryptedDriver(
        databaseName: String,
        encryptionKey: DatabaseEncryptionKey,
        useNoBackupDirectory: Boolean = true,
        walletId: String = databaseName,
    ): SqlDriver {
        val driver = runCatching {
            System.loadLibrary("sqlcipher")
            AndroidSqliteDriver(
                schema = WalletPersistenceDatabase.Schema,
                context = context,
                name = "$databaseName.db",
                factory = SupportOpenHelperFactory(encryptionKey.material),
                useNoBackupDirectory = useNoBackupDirectory,
            )
        }.getOrElse { cause ->
            throw WalletPersistenceException.EncryptionConfigurationFailed(walletId, cause)
        }

        return runCatching {
            driver.executeQuery(
                identifier = null,
                sql = "SELECT count(*) FROM sqlite_master",
                mapper = { cursor ->
                    QueryResult.Value(cursor.next().value)
                },
                parameters = 0,
                binders = null,
            )
            driver
        }.getOrElse { cause ->
            driver.close()
            throw WalletPersistenceException.DatabaseUnlockFailed(walletId, cause)
        }
    }
}
