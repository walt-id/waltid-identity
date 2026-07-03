package id.walt.wallet2.persistence.stores

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.DatabaseFileContext
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.persistence.encryption.WalletPersistenceException

/**
 * iOS SQLDelight driver factory for native SQLite wallet databases.
 */
actual class DriverFactory {
    /**
     * Creates a SQLCipher-backed native driver for [databaseName].
     *
     * @param encryptionKey Raw database key used by SQLCipher.
     * @param isDeviceLocal Reserved for platform parity; iOS database-key locality is controlled by Keychain attributes.
     * @param walletId Wallet identifier used in typed persistence errors.
     */
    actual fun createEncryptedDriver(
        databaseName: String,
        encryptionKey: DatabaseEncryptionKey,
        isDeviceLocal: Boolean,
        walletId: String,
    ): SqlDriver {
        val driver = runCatching {
            NativeSqliteDriver(
                schema = WalletPersistenceDatabase.Schema,
                name = "$databaseName.db",
                onConfiguration = { configuration ->
                    configuration.copy(
                        encryptionConfig = DatabaseConfiguration.Encryption(
                            key = encryptionKey.material.toSqlCipherPassphrase(),
                        )
                    )
                },
            )
        }.getOrElse { cause ->
            throw WalletPersistenceException.DatabaseUnlockFailed(walletId, cause)
        }

        return runCatching {
            val cipherVersion = driver.executeQuery(
                identifier = null,
                sql = "PRAGMA cipher_version;",
                mapper = { cursor ->
                    QueryResult.Value(
                        if (cursor.next().value) {
                            cursor.getString(0)
                        } else {
                            null
                        },
                    )
                },
                parameters = 0,
                binders = null,
            ).value

            if (cipherVersion.isNullOrBlank()) {
                throw WalletPersistenceException.EncryptionConfigurationFailed(walletId)
            }

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
            if (cause is WalletPersistenceException.EncryptionConfigurationFailed) {
                throw cause
            }
            throw WalletPersistenceException.DatabaseUnlockFailed(walletId, cause)
        }
    }

    private fun ByteArray.toSqlCipherPassphrase(): String =
        joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }

    /**
     * Deletes the native database file and SQLite sidecar files for [databaseName].
     */
    actual fun deleteDatabase(databaseName: String) {
        DatabaseFileContext.deleteDatabase("$databaseName.db")
    }
}
