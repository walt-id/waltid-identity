package id.walt.ktorauthnz.methods

import id.walt.errors.HttpStatusError
import id.walt.ktorauthnz.KtorAuthnz
import id.walt.ktorauthnz.accounts.InMemoryAccountStore
import id.walt.ktorauthnz.accounts.identifiers.methods.LDAPIdentifier
import id.walt.ktorauthnz.attempts.AttemptLimits
import id.walt.ktorauthnz.ephemeral.InMemoryExpiringStore
import id.walt.ktorauthnz.examples.multitenant.ExampleDirectory
import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.flows.authFlows
import id.walt.ktorauthnz.methods.config.LDAPConfiguration
import id.walt.ktorauthnz.sessions.InMemorySessionStore
import id.walt.ktorauthnz.tokens.ktorauthnztoken.KtorAuthNzTokenHandler
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class LdapLoginTest {

    private val directory = ExampleDirectory(mapOf("carol" to "ldap-secret", "dave" to "dave-secret"))
    private val accounts = InMemoryAccountStore()

    @AfterTest
    fun stop() = directory.close()

    private fun config(url: String = directory.url) = LDAPConfiguration(url, "uid=%s,ou=people,dc=org3")

    @OptIn(ExperimentalUuidApi::class)
    private fun ldapTest(url: String = directory.url, registers: Boolean = false, block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val flow = AuthFlow.fromConfig("""{"method": "ldap", "success": true, "config": {"ldapServerUrl": "$url", "userDNFormat": "uid=%s,ou=people,dc=org3"}}""")
        application {
            install(KtorAuthnz) {
                accountStore = accounts
                sessionStore = InMemorySessionStore()
                tokenHandler = KtorAuthNzTokenHandler()
                expiringStore = InMemoryExpiringStore()
                attemptLimits = AttemptLimits.DISABLED
            }
            install(ContentNegotiation) { json() }
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    val status = (cause as? HttpStatusError)?.status?.let(HttpStatusCode::fromValue) ?: HttpStatusCode.InternalServerError
                    call.respondText(cause.message ?: "", status = status)
                }
            }
            routing {
                route("auth") {
                    authFlows(listOf(flow)) {
                        if (registers) registerUnknownAccounts(LDAP) { accounts.addAccountIdentifierToAccount(Uuid.random().toString(), it) }
                    }
                }
            }
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.login(name: String, password: String) = client.post("/auth/ldap") {
        contentType(ContentType.Application.Json); setBody("""{"username": "$name", "password": "$password"}""")
    }

    @Test
    fun `a login name is escaped into the DN`() {
        assertEquals("uid=carol\\,ou\\=admins,ou=people,dc=org3", LDAP.userDn(config(), "carol,ou=admins"))
    }

    @Test
    fun `server URLs`() {
        LDAP.connectionConfig(config("ldaps://ldap.example.com")).run {
            assertEquals("ldap.example.com", ldapHost); assertEquals(636, ldapPort); assertTrue(isUseSsl)
        }
        LDAP.connectionConfig(config("ldap://ldap.example.com")).run { assertEquals(389, ldapPort); assertFalse(isUseSsl) }
        LDAP.connectionConfig(config("ldap://10.0.0.1:1389")).run { assertEquals(1389, ldapPort) }
        assertFailsWith<IllegalArgumentException> { LDAP.connectionConfig(config("http://ldap.example.com")) }
    }

    @Test
    fun `a known user logs in with the directory password`() = ldapTest {
        accounts.addAccount(LDAPIdentifier(directory.url, "carol"))
        assertEquals(HttpStatusCode.OK, login("carol", "ldap-secret").status)
    }

    @Test
    fun `a wrong password and an unknown user look the same`() = ldapTest {
        val wrong = login("carol", "wrong")
        val unknown = login("nobody", "whatever")
        assertEquals(HttpStatusCode.Unauthorized, wrong.status)
        assertEquals(wrong.status to wrong.bodyAsText(), unknown.status to unknown.bodyAsText())
    }

    @Test
    fun `an empty password is refused without asking the server`() = ldapTest(url = "ldap://127.0.0.1:1") {
        // Port 1 has no server: a bind attempt would answer 503, so 401 means none was made.
        assertEquals(HttpStatusCode.Unauthorized, login("carol", "").status)
        assertEquals(HttpStatusCode.ServiceUnavailable, login("carol", "x").status)
    }

    @Test
    fun `a directory user unknown to the application needs registration`() = ldapTest {
        assertEquals(HttpStatusCode.NotFound, login("dave", "dave-secret").status)
    }

    @Test
    fun `with registration, a directory user unknown to the application gets an account`() = ldapTest(registers = true) {
        assertEquals(HttpStatusCode.OK, login("dave", "dave-secret").status)
        assertNotNull(accounts.lookupAccountUuid(LDAPIdentifier(directory.url, "dave")))
    }
}
