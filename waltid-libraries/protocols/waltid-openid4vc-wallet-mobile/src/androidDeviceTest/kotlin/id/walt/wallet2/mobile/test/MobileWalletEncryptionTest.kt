package id.walt.wallet2.mobile.test

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.test.platform.app.InstrumentationRegistry
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.encryption.AndroidDatabaseEncryptionKeyProvider
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.persistence.encryption.WalletPersistenceException
import id.walt.wallet2.persistence.stores.DriverFactory
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class MobileWalletEncryptionTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun sdkManagedAndroidDatabaseKeyIsReusedAndDeleted() = runBlocking {
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
            useNoBackupDirectory = false,
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
            useNoBackupDirectory = false,
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
                useNoBackupDirectory = false,
                walletId = "android-wrong-key-wallet",
            )
        }

        context.deleteDatabase("$databaseName.db")
    }
}
