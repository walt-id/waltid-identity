package id.walt.webwallet.service.account.authnz

import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.InMemoryAccountStore
import id.walt.ktorauthnz.accounts.PasswordHashUpgradeStore
import id.walt.ktorauthnz.accounts.identifiers.methods.EmailIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.OIDCIdentifier
import id.walt.ktorauthnz.methods.EmailPass
import id.walt.ktorauthnz.exceptions.InvalidCredentialsException
import id.walt.ktorauthnz.methods.storeddata.EmailPassStoredData
import id.walt.ktorauthnz.security.PasswordHash
import id.walt.ktorauthnz.security.PasswordHashing
import id.walt.ktorauthnz.security.PasswordHashingConfiguration
import id.walt.ktorauthnz.security.PasswordHashingAlgorithm.ARGON2
import id.walt.ktorauthnz.security.PasswordHashingAlgorithm.BCRYPT
import id.walt.webwallet.config.KtorAuthnzConfig
import id.walt.webwallet.web.plugins.configureWalletPasswordAuthentication
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
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
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
    fun `configured wallet passwords coexist with pre-upgrade SQL credentials across restarts`() = runBlocking {
        val previousConfig = KtorAuthnzManager.passwordHashingConfig
        val previousStore = runCatching { KtorAuthnzManager.accountStore }.getOrElse { InMemoryAccountStore() }
        val legacy = EmailIdentifier("legacy@example.test")
        val fresh = EmailIdentifier("fresh@example.test")
        val configuredPepper = "wallet-test-key"
        val config = KtorAuthnzConfig(requireHttps = false, pepper = configuredPepper, hashAlgorithm = BCRYPT, cookieDomain = null)
        // Frozen output of the pre-1.3 Password4j writer, including its Argon2 secret input.
        val oldHash = "ARGON2/\$argon2id\$v=19\$m=15360,t=2,p=1" +
                "\$F11DstKLjiZjsDWf54u5C2dhuyXeognrwnB11zCp50JjLFkMk2LhJW7oG/SoV9jdfdGETiscx3jb17cxxHb9pA" +
                "\$7iRQ3t5hvDkYJdFFctIEU+19B0q2CG2Qn+43xDVA/Zs"
        suspend fun persist(identifier: EmailIdentifier, data: EmailPassStoredData) {
            val store = KtorAuthnzManager.accountStore
            val account = UUID.randomUUID().toString()
            store.addAccountIdentifierToAccount(account, identifier)
            store.addAccountStoredData(account, EmailPass.id, data)
        }
        suspend fun stored(identifier: EmailIdentifier) = assertNotNull(
            (KtorAuthnzManager.accountStore.lookupStoredDataForAccountIdentifier(identifier, EmailPass) as EmailPassStoredData).passwordHash
        )
        try {
            val bridge = config.copy(legacyPasswordWritesDuringRollout = true, hashMigrations = mapOf(ARGON2 to BCRYPT))
            configureWalletPasswordAuthentication(bridge)
            persist(legacy, EmailPassStoredData(passwordHash = oldHash))
            val staged = EmailIdentifier("staged@example.test")
            persist(staged, EmailPassStoredData(password = "bridge-fixture"))
            val stagedHash = stored(staged)
            assertTrue(stagedHash.startsWith("ARGON2/\$argon2"))
            // Read a bridge write using the historical configuration and ordinary, untagged format.
            KtorAuthnzManager.accountStore = InMemoryAccountStore()
            KtorAuthnzManager.passwordHashingConfig = PasswordHashingConfiguration(pepper = "waltid-ktor-authnz")
            assertTrue(PasswordHashing.check("bridge-fixture", PasswordHash.fromString(stagedHash)).valid)

            configureWalletPasswordAuthentication(config)
            persist(fresh, EmailPassStoredData(password = "fresh-wallet-fixture"))
            val newHash = stored(fresh)
            assertEquals(BCRYPT, PasswordHash.fromString(newHash).algorithm)
            assertEquals(fresh, EmailPass.verifyPassword(fresh.email, "fresh-wallet-fixture"))
            assertEquals(legacy, EmailPass.verifyPassword(legacy.email, "legacy-wallet-fixture"))
            assertEquals(oldHash, stored(legacy))

            configureWalletPasswordAuthentication(bridge)
            assertEquals(fresh, EmailPass.verifyPassword(fresh.email, "fresh-wallet-fixture"))
            assertEquals(legacy, EmailPass.verifyPassword(legacy.email, "legacy-wallet-fixture"))
            assertEquals(oldHash, stored(legacy))

            configureWalletPasswordAuthentication(config.copy(pepper = "different-wallet-key"))
            assertFailsWith<InvalidCredentialsException> { EmailPass.verifyPassword(fresh.email, "fresh-wallet-fixture") }
            assertEquals(newHash, stored(fresh))
            assertEquals(legacy, EmailPass.verifyPassword(legacy.email, "legacy-wallet-fixture"))

            configureWalletPasswordAuthentication(config.copy(hashMigrations = mapOf(ARGON2 to BCRYPT)))
            assertFailsWith<InvalidCredentialsException> { EmailPass.verifyPassword(legacy.email, "incorrect") }
            assertEquals(oldHash, stored(legacy))
            assertEquals(legacy, EmailPass.verifyPassword(legacy.email, "legacy-wallet-fixture"))
            val upgraded = stored(legacy)
            assertEquals(BCRYPT, PasswordHash.fromString(upgraded).algorithm)
            configureWalletPasswordAuthentication(config)
            assertEquals(legacy, EmailPass.verifyPassword(legacy.email, "legacy-wallet-fixture"))
            assertEquals(upgraded, stored(legacy))

            configureWalletPasswordAuthentication(config.copy(hashAlgorithm = ARGON2, hashMigrations = mapOf(ARGON2 to ARGON2)))
            assertEquals(staged, EmailPass.verifyPassword(staged.email, "bridge-fixture"))
            val rekeyed = stored(staged)
            assertNotEquals(stagedHash, rekeyed)
            assertEquals(staged, EmailPass.verifyPassword(staged.email, "bridge-fixture"))
            assertEquals(rekeyed, stored(staged))
        } finally {
            KtorAuthnzManager.passwordHashingConfig = previousConfig
            KtorAuthnzManager.accountStore = previousStore
        }
    }

    @Test
    fun `a stale login upgrade preserves the newer SQL credential`() = runBlocking {
        val identifier = EmailIdentifier("migration@example.test")
        val store = assertIs<PasswordHashUpgradeStore>(authenticationService.editableAccountStore)
        store.addAccountIdentifierToAccount(UUID.randomUUID().toString(), identifier)
        val original = EmailPassStoredData(passwordHash = PasswordHashing.hash("fixture").toString())
        store.addAccountIdentifierStoredData(identifier, EmailPass.id, original)
        val upgraded = PasswordHashing.hash("fixture")
        store.upgradePasswordHash(identifier, original, upgraded)
        store.upgradePasswordHash(identifier, original, PasswordHashing.hash("different fixture"))
        assertEquals(original.withPasswordHash(upgraded), store.lookupStoredDataForAccountIdentifier(identifier, EmailPass))
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
