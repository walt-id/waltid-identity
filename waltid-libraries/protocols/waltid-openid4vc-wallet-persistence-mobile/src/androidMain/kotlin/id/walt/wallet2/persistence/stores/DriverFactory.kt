package id.walt.wallet2.persistence.stores

import android.content.Context
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.persistence.encryption.WalletPersistenceException
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

/**
 * Android SQLDelight driver factory for app-private SQLite wallet databases.
 *
 * @param context Android context used by [AndroidSqliteDriver].
 */
actual class DriverFactory(private val context: Context) {
    /**
     * Creates a SQLCipher-backed Android driver for [databaseName].
     *
     * @param encryptionKey Raw database key used by SQLCipher.
     * @param isDeviceLocal Stores the database under Android's no-backup directory when true.
     * @param walletId Wallet identifier used in typed persistence errors.
     */
    actual fun createEncryptedDriver(
        databaseName: String,
        encryptionKey: DatabaseEncryptionKey,
        isDeviceLocal: Boolean,
        walletId: String,
    ): SqlDriver {
        val driver = runCatching {
            System.loadLibrary("sqlcipher")
            AndroidSqliteDriver(
                schema = WalletPersistenceDatabase.Schema,
                context = context,
                name = "$databaseName.db",
                factory = SupportOpenHelperFactory(encryptionKey.material),
                useNoBackupDirectory = isDeviceLocal,
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

    /**
     * Deletes the Android database file and SQLite sidecar files for [databaseName].
     */
    actual fun deleteDatabase(databaseName: String) {
        val fileName = "$databaseName.db"
        context.deleteDatabase(fileName)
        deleteDatabaseFiles(context.getDatabasePath(fileName))
        deleteDatabaseFiles(File(context.noBackupFilesDir, fileName))
    }

    private fun deleteDatabaseFiles(databaseFile: File): Boolean {
        var deleted = databaseFile.delete()
        deleted = File("${databaseFile.absolutePath}-journal").delete() || deleted
        deleted = File("${databaseFile.absolutePath}-shm").delete() || deleted
        deleted = File("${databaseFile.absolutePath}-wal").delete() || deleted
        databaseFile.parentFile
            ?.listFiles { file -> file.name.startsWith("${databaseFile.name}-mj") }
            ?.forEach { file -> deleted = file.delete() || deleted }
        return deleted
    }
}
