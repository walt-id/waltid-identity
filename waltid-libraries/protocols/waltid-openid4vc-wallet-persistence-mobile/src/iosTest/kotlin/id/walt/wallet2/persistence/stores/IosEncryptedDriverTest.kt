package id.walt.wallet2.persistence.stores

import co.touchlab.sqliter.DatabaseFileContext
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.persistence.encryption.WalletPersistenceException
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.posix.S_IRWXU
import platform.posix.chmod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
            val databasePath = DatabaseFileContext.databasePath("$name.db", null)
            assertFalse(NSFileManager.defaultManager.fileExistsAtPath(databasePath))
            val foreground = factory.createEncryptedDriver(name, key)
            try {
                val foregroundQueries = WalletPersistenceDatabase(foreground).walletPersistenceQueries
                foregroundQueries.setCredentialStatusMaintenance(name, "grant")
                val maintenance = assertNotNull(open())
                try {
                    val queries = WalletPersistenceDatabase(maintenance).walletPersistenceQueries
                    queries.transaction {
                        assertEquals("grant", queries.selectCredentialStatusMaintenance(name).executeAsOne().grant_id)
                        queries.setCredentialStatusMaintenance(name, null)
                    }
                    assertNull(foregroundQueries.selectCredentialStatusMaintenance(name).executeAsOne().grant_id)
                } finally {
                    maintenance.close()
                }
                assertFailsWith<StatusMaintenanceStorageException> {
                    factory.openExistingEncryptedDriver(name, location, DatabaseEncryptionKey(name, ByteArray(32)), name)
                }
            } finally {
                foreground.close()
            }
        } finally {
            factory.deleteDatabase(name)
        }
        assertNull(open())
    }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun maintenanceDefersWhenTheDatabaseParentIsInaccessible() {
        val name = "ios_inaccessible_wallet_${Uuid.random()}"
        val factory = DriverFactory()
        val key = DatabaseEncryptionKey(name, ByteArray(32) { it.toByte() })
        val databasePath = DatabaseFileContext.databasePath("$name.db", null)
        val isolatedDirectory = "$databasePath-directory"
        val isolatedDatabase = "$isolatedDirectory/wallet.db"
        val files = NSFileManager.defaultManager
        fun open() = factory.openExistingEncryptedDriver(name, factory.existingLocation(), key, name)

        try {
            factory.createEncryptedDriver(name, key).close()
            assertTrue(files.createDirectoryAtPath(isolatedDirectory, false, null, null))
            assertTrue(files.moveItemAtPath(databasePath, isolatedDatabase, null))
            // Keep the permission change confined to this test's database.
            assertTrue(files.createSymbolicLinkAtPath(databasePath, isolatedDatabase, null))
            try {
                assertEquals(0, chmod(isolatedDirectory, 0u))
                assertFailsWith<StatusMaintenanceStorageException> { open() }
            } finally {
                assertEquals(0, chmod(isolatedDirectory, S_IRWXU.toUShort()))
            }
            assertNotNull(open()).close()
        } finally {
            factory.deleteDatabase(name)
            files.removeItemAtPath(isolatedDirectory, null)
        }
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
