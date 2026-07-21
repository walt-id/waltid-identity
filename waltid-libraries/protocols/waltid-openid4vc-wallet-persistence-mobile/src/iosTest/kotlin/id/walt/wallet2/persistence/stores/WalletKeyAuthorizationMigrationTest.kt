package id.walt.wallet2.persistence.stores

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import id.walt.crypto.keys.KeyUseAuthorizationPolicy
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class WalletKeyAuthorizationMigrationTest {

    @Test
    fun existingKeyRowsMigrateToNoneWithoutInventingHardwareBacking() {
        val databaseName = "wallet_key_policy_migration_${Uuid.random()}"
        val key = DatabaseEncryptionKey(
            keyId = databaseName,
            material = ByteArray(32) { (it + 1).toByte() },
        )
        val legacyDriver = NativeSqliteDriver(
            schema = LegacyWalletSchema,
            name = "$databaseName.db",
            onConfiguration = { configuration ->
                configuration.copy(
                    encryptionConfig = DatabaseConfiguration.Encryption(key.material.toPassphrase()),
                )
            },
        )
        legacyDriver.execute(
            identifier = null,
            sql = """
                INSERT INTO key_references (
                    key_id, key_type, created_at, is_platform_backed, key_material
                ) VALUES ('legacy-key', 'secp256r1', 1, 1, NULL)
            """.trimIndent(),
            parameters = 0,
        )
        legacyDriver.close()

        val factory = DriverFactory()
        try {
            val migratedDriver = factory.createEncryptedDriver(
                databaseName = databaseName,
                encryptionKey = key,
                isDeviceLocal = true,
                walletId = databaseName,
            )
            val reference = WalletPersistenceDatabase(migratedDriver)
                .walletPersistenceQueries
                .selectByKeyId("legacy-key")
                .executeAsOne()

            assertEquals(KeyUseAuthorizationPolicy.None.name, reference.requested_authorization_policy)
            assertEquals(KeyUseAuthorizationPolicy.None.name, reference.effective_authorization_policy)
            assertNull(reference.effective_hardware_backing)
            migratedDriver.close()
        } finally {
            factory.deleteDatabase(databaseName)
        }
    }

    private fun ByteArray.toPassphrase(): String = joinToString(separator = "") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }

    private object LegacyWalletSchema : SqlSchema<QueryResult.Value<Unit>> {
        override val version: Long = 1

        override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
            driver.execute(
                null,
                """
                    CREATE TABLE key_references (
                        key_id TEXT NOT NULL PRIMARY KEY,
                        key_type TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        is_platform_backed INTEGER NOT NULL DEFAULT 1,
                        key_material TEXT
                    )
                """.trimIndent(),
                0,
            )
            driver.execute(
                null,
                """
                    CREATE TABLE credentials (
                        id TEXT NOT NULL PRIMARY KEY,
                        serialized_credential TEXT NOT NULL,
                        format TEXT NOT NULL DEFAULT '',
                        label TEXT,
                        added_at INTEGER NOT NULL
                    )
                """.trimIndent(),
                0,
            )
            driver.execute(
                null,
                """
                    CREATE TABLE dids (
                        did TEXT NOT NULL PRIMARY KEY,
                        document TEXT NOT NULL
                    )
                """.trimIndent(),
                0,
            )
            return QueryResult.Unit
        }

        override fun migrate(
            driver: SqlDriver,
            oldVersion: Long,
            newVersion: Long,
            vararg callbacks: AfterVersion,
        ): QueryResult.Value<Unit> = QueryResult.Unit
    }
}
