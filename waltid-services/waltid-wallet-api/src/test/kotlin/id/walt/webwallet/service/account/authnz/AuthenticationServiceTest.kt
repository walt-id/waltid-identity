package id.walt.webwallet.service.account.authnz

import id.walt.ktorauthnz.accounts.identifiers.methods.EmailIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.OIDCIdentifier
import id.walt.webwallet.db.models.authnz.AuthnzAccountIdentifiers
import id.walt.webwallet.db.models.authnz.AuthnzStoredData
import id.walt.webwallet.db.models.authnz.AuthnzUsers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthenticationServiceTest {

    private lateinit var authenticationService: AuthenticationService
    private lateinit var database: Database
    private val testDbFile = File.createTempFile("authnz_test_", ".db")

    @BeforeAll
    fun setUpDatabase() {
        database = Database.connect(
            "jdbc:sqlite:${testDbFile.absolutePath}",
            driver = "org.sqlite.JDBC"
        )
        TransactionManager.defaultDatabase = database
        transaction(database) {
            SchemaUtils.create(AuthnzUsers, AuthnzAccountIdentifiers, AuthnzStoredData)
        }
    }

    @BeforeEach
    fun setUp() {
        authenticationService = AuthenticationService(Dispatchers.IO)
        transaction(database) {
            SchemaUtils.drop(AuthnzStoredData, AuthnzAccountIdentifiers, AuthnzUsers)
            SchemaUtils.create(AuthnzUsers, AuthnzAccountIdentifiers, AuthnzStoredData)
        }
    }

    @AfterAll
    fun tearDown() {
        transaction(database) {
            SchemaUtils.drop(AuthnzStoredData, AuthnzAccountIdentifiers, AuthnzUsers)
        }
        TransactionManager.closeAndUnregister(database)
        testDbFile.delete()
    }

    @Test
    fun `addAccountIdentifierToAccount creates user if not exists`() = runBlocking {
        val accountId = UUID.randomUUID().toString()
        val identifier = EmailIdentifier("test@example.com")

        authenticationService.editableAccountStore.addAccountIdentifierToAccount(accountId, identifier)

        transaction(database) {
            val user = AuthnzUsers.selectAll()
                .where { AuthnzUsers.id eq UUID.fromString(accountId) }
                .singleOrNull()
            assertNotNull(user, "User should be created in AuthnzUsers table")

            val accountIdentifier = AuthnzAccountIdentifiers.selectAll()
                .where { AuthnzAccountIdentifiers.userId eq UUID.fromString(accountId) }
                .singleOrNull()
            assertNotNull(accountIdentifier, "Account identifier should be created")
            assertEquals(identifier.toDataString(), accountIdentifier[AuthnzAccountIdentifiers.identifier])
        }
    }

    @Test
    fun `addAccountIdentifierToAccount does not duplicate user if already exists`() = runBlocking {
        val accountId = UUID.randomUUID().toString()
        val identifier1 = EmailIdentifier("test1@example.com")
        val identifier2 = EmailIdentifier("test2@example.com")

        authenticationService.editableAccountStore.addAccountIdentifierToAccount(accountId, identifier1)
        authenticationService.editableAccountStore.addAccountIdentifierToAccount(accountId, identifier2)

        transaction(database) {
            val userCount = AuthnzUsers.selectAll()
                .where { AuthnzUsers.id eq UUID.fromString(accountId) }
                .count()
            assertEquals(1, userCount, "Should have exactly one user record")

            val identifierCount = AuthnzAccountIdentifiers.selectAll()
                .where { AuthnzAccountIdentifiers.userId eq UUID.fromString(accountId) }
                .count()
            assertEquals(2, identifierCount, "Should have two account identifiers")
        }
    }

    @Test
    fun `addAccountIdentifierToAccount works with OIDC identifier for new user`() = runBlocking {
        val accountId = UUID.randomUUID().toString()
        val oidcIdentifier = OIDCIdentifier(
            issuer = "https://idp.example.com/realms/my-realm",
            subject = "user-subject-123"
        )

        authenticationService.editableAccountStore.addAccountIdentifierToAccount(accountId, oidcIdentifier)

        transaction(database) {
            val user = AuthnzUsers.selectAll()
                .where { AuthnzUsers.id eq UUID.fromString(accountId) }
                .singleOrNull()
            assertNotNull(user, "User should be created for OIDC login")

            val accountIdentifier = AuthnzAccountIdentifiers.selectAll()
                .where { AuthnzAccountIdentifiers.userId eq UUID.fromString(accountId) }
                .singleOrNull()
            assertNotNull(accountIdentifier, "OIDC account identifier should be created")
            assertTrue(
                accountIdentifier[AuthnzAccountIdentifiers.identifier].contains("user-subject-123"),
                "Identifier should contain the OIDC subject"
            )
        }
    }

    @Test
    fun `lookupAccountUuid returns account id when identifier exists`() = runBlocking {
        val accountId = UUID.randomUUID().toString()
        val identifier = EmailIdentifier("lookup@example.com")

        authenticationService.editableAccountStore.addAccountIdentifierToAccount(accountId, identifier)

        val result = authenticationService.editableAccountStore.lookupAccountUuid(identifier)

        assertEquals(accountId, result, "Should return the correct account ID")
    }

    @Test
    fun `lookupAccountUuid returns null when identifier does not exist`() = runBlocking {
        val identifier = EmailIdentifier("nonexistent@example.com")

        val result = authenticationService.editableAccountStore.lookupAccountUuid(identifier)

        assertNull(result, "Should return null for non-existent identifier")
    }

    @Test
    fun `removeAccountIdentifierFromAccount removes the identifier`() = runBlocking {
        val accountId = UUID.randomUUID().toString()
        val identifier = EmailIdentifier("remove@example.com")

        authenticationService.editableAccountStore.addAccountIdentifierToAccount(accountId, identifier)
        authenticationService.editableAccountStore.removeAccountIdentifierFromAccount(identifier)

        transaction(database) {
            val accountIdentifier = AuthnzAccountIdentifiers.selectAll()
                .where { AuthnzAccountIdentifiers.identifier eq identifier.toDataString() }
                .singleOrNull()
            assertNull(accountIdentifier, "Account identifier should be removed")

            val user = AuthnzUsers.selectAll()
                .where { AuthnzUsers.id eq UUID.fromString(accountId) }
                .singleOrNull()
            assertNotNull(user, "User should still exist after removing identifier")
        }
    }
}
