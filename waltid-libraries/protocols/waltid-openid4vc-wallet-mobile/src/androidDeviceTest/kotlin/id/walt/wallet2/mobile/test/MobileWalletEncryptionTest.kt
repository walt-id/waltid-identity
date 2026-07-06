package id.walt.wallet2.mobile.test

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.test.platform.app.InstrumentationRegistry
import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletDatabaseKey
import id.walt.wallet2.mobile.MobileWalletFactory
import id.walt.wallet2.mobile.MobileWalletPersistence
import id.walt.wallet2.mobile.MobileWalletStores
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.encryption.AndroidDatabaseEncryptionKeyProvider
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKeyProvider
import id.walt.wallet2.persistence.encryption.WalletPersistenceException
import id.walt.wallet2.persistence.stores.DriverFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MobileWalletEncryptionTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun managedAndroidDatabaseKeyIsReusedAndDeleted() = runBlocking {
        val provider = AndroidDatabaseEncryptionKeyProvider(context)
        val walletId = "android-encryption-key-test"
        val databaseName = "wallet_$walletId"

        provider.deleteKey(walletId, databaseName)

        val first = provider.getOrCreateKey(walletId, databaseName)
        val second = provider.getOrCreateKey(walletId, databaseName)
        assertContentEquals(first.material, second.material)

        provider.deleteKey(walletId, databaseName)

        val regenerated = provider.getOrCreateKey(walletId, databaseName)
        assertFalse(first.material.contentEquals(regenerated.material))

        provider.deleteKey(walletId, databaseName)
    }

    @Test
    fun encryptedAndroidDatabaseCannotBeOpenedByPlainSqlite() {
        val databaseName = "wallet_android_encryption_plain_sqlite_test"
        val databaseFile = context.getDatabasePath("$databaseName.db")
        context.deleteDatabase("$databaseName.db")

        val driver = DriverFactory(context).createEncryptedDriver(
            databaseName = databaseName,
            encryptionKey = DatabaseEncryptionKey(
                keyId = "test-key",
                material = ByteArray(32) { index -> index.toByte() },
            ),
            isDeviceLocal = false,
            walletId = databaseName,
        )
        val database = WalletPersistenceDatabase(driver)
        database.walletPersistenceQueries.insertDid(
            did = "did:example:encrypted",
            document = """{"id":"did:example:encrypted"}""",
        )
        driver.close()

        assertFailsWith<SQLiteException> {
            SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            ).use { plainDb ->
                plainDb.rawQuery("SELECT document FROM dids", emptyArray()).use { cursor ->
                    cursor.moveToFirst()
                }
            }
        }

        context.deleteDatabase("$databaseName.db")
    }

    @Test
    fun encryptedAndroidDatabaseRejectsWrongKeyWithTypedError() {
        val databaseName = "wallet_android_encryption_wrong_key_test"
        context.deleteDatabase("$databaseName.db")

        val rightKey = DatabaseEncryptionKey(
            keyId = "right-key",
            material = ByteArray(32) { index -> index.toByte() },
        )
        val wrongKey = DatabaseEncryptionKey(
            keyId = "wrong-key",
            material = ByteArray(32) { index -> (index + 1).toByte() },
        )

        val driver = DriverFactory(context).createEncryptedDriver(
            databaseName = databaseName,
            encryptionKey = rightKey,
            isDeviceLocal = false,
            walletId = databaseName,
        )
        val database = WalletPersistenceDatabase(driver)
        database.walletPersistenceQueries.insertDid(
            did = "did:example:right-key",
            document = """{"id":"did:example:right-key"}""",
        )
        driver.close()

        assertFailsWith<WalletPersistenceException.DatabaseUnlockFailed> {
            DriverFactory(context).createEncryptedDriver(
                databaseName = databaseName,
                encryptionKey = wrongKey,
                isDeviceLocal = false,
                walletId = "android-wrong-key-wallet",
            )
        }

        context.deleteDatabase("$databaseName.db")
    }

    @Test
    fun deleteWalletRemovesManagedAndroidDatabaseAndKey() = runBlocking {
        val walletId = "android-encryption-delete-test"
        val databaseName = "wallet_$walletId"
        val databaseFileName = "$databaseName.db"
        val provider = AndroidDatabaseEncryptionKeyProvider(context)
        provider.deleteKey(walletId, databaseName)
        deleteDatabaseFiles(databaseFileName)

        val firstKey = provider.getOrCreateKey(walletId, databaseName)
        val wallet = MobileWalletFactory(context).create(
            MobileWalletConfig(walletId = walletId)
        )
        assertTrue(databaseFiles(databaseFileName).any { it.exists() }, "Encrypted wallet DB should exist before deletion")

        wallet.deleteWallet()

        assertFalse(databaseFiles(databaseFileName).any { it.exists() }, "Managed wallet DB files should be deleted")
        val regeneratedKey = provider.getOrCreateKey(walletId, databaseName)
        assertFalse(firstKey.material.contentEquals(regeneratedKey.material), "DB key should be regenerated after wallet deletion")

        provider.deleteKey(walletId, databaseName)
        deleteDatabaseFiles(databaseFileName)
    }

    @Test
    fun providedAndroidDatabaseKeyOpensEncryptedWalletAndIsDeleted() = runBlocking {
        val walletId = "android-provided-key-test"
        val databaseName = "wallet_$walletId"
        val databaseFileName = "$databaseName.db"
        val provider = RecordingDatabaseKeyProvider(
            DatabaseEncryptionKey(
                keyId = "provided-key",
                material = ByteArray(32) { index -> (index + 3).toByte() },
            )
        )
        deleteDatabaseFiles(databaseFileName)

        val factory = MobileWalletFactory(context)
        val config = MobileWalletConfig(
            walletId = walletId,
            persistence = MobileWalletPersistence(
                databaseKey = MobileWalletDatabaseKey.Provided(provider),
            ),
        )
        val wallet = factory.create(config)

        val bootstrap = wallet.bootstrap()
        val reopenedBootstrap = factory.create(config).bootstrap()

        assertEquals(bootstrap, reopenedBootstrap)
        assertEquals(listOf("$walletId:$databaseName", "$walletId:$databaseName"), provider.requestedKeys)
        wallet.deleteWallet()
        assertEquals(listOf("$walletId:$databaseName"), provider.deletedKeys)
        assertFalse(databaseFiles(databaseFileName).any { it.exists() }, "Provided-key wallet DB files should be deleted")

        deleteDatabaseFiles(databaseFileName)
    }

    @Test
    fun customAndroidCredentialStoreRetainsPlatformSigningKeys() = runBlocking {
        val walletId = "android-custom-credentials-test"
        val databaseName = "wallet_$walletId"
        val databaseFileName = "$databaseName.db"
        val credentialStore = RecordingCredentialStore()
        val config = MobileWalletConfig(
            walletId = walletId,
            persistence = MobileWalletPersistence(
                stores = MobileWalletStores(credentials = credentialStore),
            ),
        )
        val factory = MobileWalletFactory(context)
        deleteDatabaseFiles(databaseFileName)

        val wallet = factory.create(config)

        val bootstrap = wallet.bootstrap()
        val credentials = wallet.credentials()
        val reopenedWallet = factory.create(config)
        val reopenedBootstrap = reopenedWallet.bootstrap()
        val reopenedCredentials = reopenedWallet.credentials()

        assertTrue(bootstrap.did.startsWith("did:"), "Custom credential stores should keep Android platform signing keys")
        assertEquals(emptyList(), credentials)
        assertEquals(bootstrap.did, reopenedBootstrap.did, "Default DID store should survive wallet recreation")
        assertEquals(bootstrap.keyId, reopenedBootstrap.keyId, "Platform signing-key reference should survive wallet recreation")
        assertEquals(emptyList(), reopenedCredentials)
        assertEquals(2, credentialStore.listCredentialsCalls)

        wallet.deleteWallet()
        deleteDatabaseFiles(databaseFileName)
        AndroidDatabaseEncryptionKeyProvider(context).deleteKey(walletId, databaseName)
    }

    private fun deleteDatabaseFiles(databaseFileName: String) {
        databaseFiles(databaseFileName).forEach { file ->
            file.delete()
            file.parentFile?.listFiles { candidate -> candidate.name.startsWith("${file.name}-mj") }
                ?.forEach { it.delete() }
        }
        context.deleteDatabase(databaseFileName)
    }

    private fun databaseFiles(databaseFileName: String): List<File> {
        val baseFiles = listOf(
            context.getDatabasePath(databaseFileName),
            File(context.noBackupFilesDir, databaseFileName),
        )
        return baseFiles.flatMap { file ->
            listOf(
                file,
                File("${file.absolutePath}-journal"),
                File("${file.absolutePath}-shm"),
                File("${file.absolutePath}-wal"),
            )
        }
    }

    private class RecordingDatabaseKeyProvider(
        private val key: DatabaseEncryptionKey,
    ) : DatabaseEncryptionKeyProvider {
        val requestedKeys = mutableListOf<String>()
        val deletedKeys = mutableListOf<String>()

        override suspend fun getOrCreateKey(walletId: String, databaseName: String): DatabaseEncryptionKey {
            requestedKeys += "$walletId:$databaseName"
            return key
        }

        override suspend fun deleteKey(walletId: String, databaseName: String) {
            deletedKeys += "$walletId:$databaseName"
        }
    }

    private class RecordingCredentialStore : WalletCredentialStore {
        var listCredentialsCalls = 0

        override suspend fun getCredential(id: String): StoredCredential? = null

        override suspend fun listCredentials(): Flow<StoredCredential> {
            listCredentialsCalls++
            return emptyList<StoredCredential>().asFlow()
        }

        override suspend fun addCredential(entry: StoredCredential) =
            error("This test only verifies credential-store routing")

        override suspend fun removeCredential(id: String): Boolean = false
    }
}
