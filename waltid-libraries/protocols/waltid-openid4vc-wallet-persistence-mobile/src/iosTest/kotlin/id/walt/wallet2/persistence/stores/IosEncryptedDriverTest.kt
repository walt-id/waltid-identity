package id.walt.wallet2.persistence.stores

import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.persistence.encryption.WalletPersistenceException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class IosEncryptedDriverTest {
    @Test
    fun maintenanceRefusesMissingOrWronglyKeyedFilesAndUsesTheExistingSchema() {
        val name = "ios_existing_wallet_${Uuid.random()}"
        val factory = DriverFactory()
        val key = DatabaseEncryptionKey(name, ByteArray(32) { it.toByte() })
        val location = factory.existingLocation()
        fun open() = factory.openExistingEncryptedDriver(name, location, key, name)
        try {
            assertNull(open())
            factory.createEncryptedDriver(name, key).use { foreground ->
                val foregroundQueries = WalletPersistenceDatabase(foreground).walletPersistenceQueries
                foregroundQueries.setCredentialStatusMaintenance(name, "grant")
                assertNotNull(open()).use { maintenance ->
                    val queries = WalletPersistenceDatabase(maintenance).walletPersistenceQueries
                    queries.transaction {
                        assertEquals("grant", queries.selectCredentialStatusMaintenance(name).executeAsOne().grant_id)
                        queries.setCredentialStatusMaintenance(name, null)
                    }
                    assertNull(foregroundQueries.selectCredentialStatusMaintenance(name).executeAsOne().grant_id)
                }
                assertFailsWith<StatusMaintenanceStorageException> {
                    factory.openExistingEncryptedDriver(name, location, DatabaseEncryptionKey(name, ByteArray(32)), name)
                }
            }
        } finally {
            factory.deleteDatabase(name)
        }
        assertNull(open())
    }


    @Test
    fun encryptedIosDatabaseRejectsWrongKeyWithTypedError() {
        val databaseName = "ios_encrypted_driver_${Uuid.random()}"
        val correctKey = DatabaseEncryptionKey(
            keyId = "$databaseName-correct",
            material = ByteArray(32) { it.toByte() },
        )
        val wrongKey = DatabaseEncryptionKey(
            keyId = "$databaseName-wrong",
            material = ByteArray(32) { (it + 1).toByte() },
        )

        DriverFactory().createEncryptedDriver(
            databaseName = databaseName,
            encryptionKey = correctKey,
            isDeviceLocal = true,
            walletId = databaseName,
        ).close()

        assertFailsWith<WalletPersistenceException.DatabaseUnlockFailed> {
            DriverFactory().createEncryptedDriver(
                databaseName = databaseName,
                encryptionKey = wrongKey,
                isDeviceLocal = true,
                walletId = databaseName,
            )
        }
    }
}
