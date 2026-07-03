package id.walt.wallet2.persistence.stores

import app.cash.sqldelight.db.QueryResult
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class SqlCipherLinkageTest {

    @Test
    fun nativeSqliteReportsSqlCipherVersion() {
        val databaseName = "sqlcipher_linkage_proof_${Uuid.random()}"
        val driver = DriverFactory().createEncryptedDriver(
            databaseName = databaseName,
            encryptionKey = DatabaseEncryptionKey(
                keyId = "$databaseName-key",
                material = ByteArray(32) { index -> index.toByte() },
            ),
            isDeviceLocal = true,
            walletId = databaseName,
        )
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

        driver.close()

        assertNotNull(cipherVersion)
        assertTrue(cipherVersion.isNotBlank())
    }
}
