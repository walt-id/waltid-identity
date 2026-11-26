package id.walt.wallet2

import id.walt.ktorauthnz.accounts.identifiers.methods.EmailIdentifier
import id.walt.ktorauthnz.methods.EmailPass
import id.walt.ktorauthnz.methods.storeddata.EmailPassStoredData
import id.walt.ktorauthnz.security.PasswordHash
import id.walt.ktorauthnz.security.PasswordHashingAlgorithm
import id.walt.wallet2.auth.ExposedWallet2AccountStore
import id.walt.wallet2.persistence.Wallet2PersistenceConfig
import id.walt.wallet2.persistence.initWallet2Database
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.DriverManager
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class Wallet2AccountStoreTest {
    @Test
    fun `failed verifier write rolls back registration`(@TempDir directory: Path) = runTest {
        val config = Wallet2PersistenceConfig(jdbcUrl = "jdbc:sqlite:${directory.resolve("accounts.db")}")
        val store = ExposedWallet2AccountStore(initWallet2Database(config))
        val email = EmailIdentifier("rollback@example.test")
        val password = "synthetic-registration-password"
        DriverManager.getConnection(config.jdbcUrl).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TRIGGER reject_verifier BEFORE INSERT ON wallet2_identifier_auth_data
                    BEGIN SELECT RAISE(ABORT, 'synthetic verifier write failure'); END
                    """.trimIndent()
                )
                val failure = assertFailsWith<SQLException> { store.register(email, password) }
                assertContains(failure.message.orEmpty(), "synthetic verifier write failure")
                assertNull(store.lookupAccountUuid(email))
                assertNull(store.lookupStoredDataForAccountIdentifier(email, EmailPass))

                statement.execute("DROP TRIGGER reject_verifier")
                val accountId = assertNotNull(store.register(email, password))
                assertEquals(accountId, store.lookupAccountUuid(email))
                statement.executeQuery("SELECT data FROM wallet2_identifier_auth_data").use { result ->
                    check(result.next())
                    assertFalse(result.getString(1).contains(password))
                }
            }
        }
    }

    @Test
    fun `upgrading a password hash cannot overwrite a replacement credential`() = runTest {
        val config = Wallet2PersistenceConfig(jdbcUrl = "jdbc:sqlite::memory:", maximumPoolSize = 1)
        val store = ExposedWallet2AccountStore(initWallet2Database(config))
        val email = EmailIdentifier("hash-upgrade@example.test")
        val accountId = assertNotNull(store.register(email, "synthetic-original-password"))
        val original = assertIs<EmailPassStoredData>(store.lookupStoredDataForAccountIdentifier(email, EmailPass))

        val changed = EmailPassStoredData(password = "synthetic-password-change").transformSavable()
        store.updateAccountIdentifierStoredData(email, EmailPass.id, changed)
        val upgrade = PasswordHash("synthetic-upgraded-hash", PasswordHashingAlgorithm.ARGON2)
        store.upgradePasswordHash(email, original, upgrade)
        assertEquals(changed, store.lookupStoredDataForAccountIdentifier(email, EmailPass))

        store.upgradePasswordHash(email, changed, upgrade)
        assertEquals(changed.withPasswordHash(upgrade), store.lookupStoredDataForAccountIdentifier(email, EmailPass))
        assertEquals(accountId, store.lookupAccountUuid(email))
    }
}
