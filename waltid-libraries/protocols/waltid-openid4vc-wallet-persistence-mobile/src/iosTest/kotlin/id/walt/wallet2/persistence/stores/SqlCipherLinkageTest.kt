package id.walt.wallet2.persistence.stores

import app.cash.sqldelight.db.QueryResult
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SqlCipherLinkageTest {

    @Test
    fun nativeSqliteReportsSqlCipherVersion() {
        val driver = DriverFactory().createDriver("sqlcipher_linkage_proof")
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
