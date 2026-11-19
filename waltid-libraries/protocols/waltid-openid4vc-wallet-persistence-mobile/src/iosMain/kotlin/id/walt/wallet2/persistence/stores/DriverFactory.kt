package id.walt.wallet2.persistence.stores

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.DatabaseFileContext
import co.touchlab.sqliter.JournalMode
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.persistence.encryption.WalletPersistenceException
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * iOS SQLDelight driver factory for native SQLite wallet databases.
 */
public actual class DriverFactory {
    private var appGroupIdentifier: String? = null

    /** Uses the shared App Group container for the database and all SQLite sidecars. */
    public fun useAppGroup(identifier: String): DriverFactory = apply {
        require(identifier.isNotBlank()) { "App Group identifier must not be blank" }
        appGroupIdentifier = identifier
    }
    /**
     * Creates a SQLCipher-backed native driver for [databaseName].
     *
     * The connection uses SQLite's rollback journal. The native driver would otherwise
     * enable WAL, which locks the `-shm` file until every connection closes. iOS then
     * terminates the suspended app with `0xdead10cc` so the Identity Document extension
     * cannot deadlock on that shared App Group file.
     *
     * @param encryptionKey Raw database key used by SQLCipher.
     * @param isDeviceLocal Reserved for platform parity; iOS database-key locality is controlled by Keychain attributes.
     * @param walletId Wallet identifier used in typed persistence errors.
     */
    public actual fun createEncryptedDriver(
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
                        journalMode = JournalMode.DELETE,
                        extendedConfig = configuration.extendedConfig.copy(basePath = sharedDatabasePath(walletId)),
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

    /** Identifies the container used by the foreground wallet. */
    public fun existingLocation(): String = appGroupIdentifier ?: "sandbox"

    /** Opens the authorized container's existing database without creating or migrating it. */
    public fun openExistingEncryptedDriver(
        databaseName: String, location: String, encryptionKey: DatabaseEncryptionKey, walletId: String,
    ): SqlDriver? {
        require(databaseName.none { it == '/' || it == '\\' || it == '\u0000' })
        if (location != existingLocation()) return null
        val directory = sharedDatabasePath(walletId) ?: run {
            // Match SQLiter's default path without its create-directory side effect.
            val support = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
                .firstOrNull() as? String ?: return null
            "$support/databases"
        }
        val path = "$directory/$databaseName.db"
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return null
        return ExistingSqlCipherDriver.open(path, encryptionKey.material.toSqlCipherPassphrase())
    }

    /**
     * Deletes the native database file and SQLite sidecar files for [databaseName].
     */
    public actual fun deleteDatabase(databaseName: String) {
        DatabaseFileContext.deleteDatabase("$databaseName.db", appGroupContainerPath())
    }

    private fun sharedDatabasePath(walletId: String): String? = runCatching { appGroupContainerPath() }
        .getOrElse { cause -> throw WalletPersistenceException.EncryptionConfigurationFailed(walletId, cause) }

    /** Returns the App Group container path, or null when this wallet is not shared with an extension. */
    private fun appGroupContainerPath(): String? = appGroupIdentifier?.let { identifier ->
        NSFileManager.defaultManager
            .containerURLForSecurityApplicationGroupIdentifier(identifier)
            ?.path
            ?: throw IllegalStateException("App Group container is unavailable: $identifier")
    }

}
