package id.walt.ktorauthnz.tenants

import id.walt.errors.HttpStatusError
import id.walt.ktorauthnz.KtorAuthnz
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.InMemoryAccountStore
import id.walt.ktorauthnz.ephemeral.InMemoryExpiringStore
import id.walt.ktorauthnz.examples.services.ExampleAuthenticator
import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.flows.authFlows
import id.walt.ktorauthnz.methods.Passkey
import id.walt.ktorauthnz.methods.config.PasskeySettings
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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

/** Tenants on their own domains: each is its own passkey relying party. */
class TenantPasskeysTest {

    private val accounts = InMemoryAccountStore()
    private val json = Json { ignoreUnknownKeys = true }
    private fun settingsOf(tenant: String) = PasskeySettings(rpId = "$tenant.example.com", rpName = tenant, origins = setOf("https://$tenant.example.com"))

    /** A device of a user of org1, on org1's site. */
    private val org1Device = ExampleAuthenticator("https://org1.example.com")

    @BeforeTest
    fun setUp() = runBlocking {
        KtorAuthnzManager.expiringStore = InMemoryExpiringStore()
        KtorAuthnzManager.passkeysPerTenant = { tenant -> tenant?.let(::settingsOf) }
        inAuthnzTenant("org1") {
            val accountId = accounts.addAccount()
            val options = Passkey.registrationOptions(Passkey.newChallenge("register:$accountId"), accountId, "alice", emptyList())
            assertEquals("org1.example.com", options["rp"]!!.jsonObject["id"]!!.jsonPrimitive.content)
            val (identifier, stored) = Passkey.verifyRegistration(org1Device.create(options), accountId, null)
            accounts.addAccountIdentifierToAccount(accountId, identifier)
            accounts.addAccountIdentifierStoredData(identifier, Passkey.id, stored)
        }
    }

    @AfterTest
    fun tearDown() {
        KtorAuthnzManager.passkeysPerTenant = null
    }

    private fun passkeyTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(KtorAuthnz) {
                accountStore = accounts
                sessionStore = InMemorySessionStore()
                tokenHandler = KtorAuthNzTokenHandler()
                expiringStore = KtorAuthnzManager.expiringStore
                passkeysPerTenant { tenant -> tenant?.let(::settingsOf) }
            }
            install(ContentNegotiation) { json() }
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    val status = (cause as? HttpStatusError)?.status?.let(HttpStatusCode::fromValue) ?: HttpStatusCode.InternalServerError
                    call.respondText(cause.message ?: "", status = status)
                }
            }
            routing {
                route("{tenant}") {
                    authnzTenant { parameters["tenant"]!! }
                    route("auth") { authFlows(listOf(AuthFlow.fromConfig("""{"method": "passkey", "success": true}"""))) }
                }
            }
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.options(tenant: String): JsonObject =
        json.parseToJsonElement(client.post("/$tenant/auth/passkey/options").bodyAsText()).jsonObject

    private suspend fun ApplicationTestBuilder.login(tenant: String, assertion: String) = client.post("/$tenant/auth/passkey") {
        contentType(ContentType.Application.Json); setBody(assertion)
    }

    @Test
    fun `each tenant is its own relying party`() = passkeyTest {
        assertEquals("org1.example.com", options("org1")["rpId"]!!.jsonPrimitive.content)
        assertEquals("org2.example.com", options("org2")["rpId"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a passkey logs in at its own tenant`() = passkeyTest {
        assertEquals(HttpStatusCode.OK, login("org1", org1Device.get(options("org1"))).status)
    }

    @Test
    fun `a passkey response for one tenant is refused by another`() = passkeyTest {
        val assertion = org1Device.get(options("org1"))
        assertEquals(HttpStatusCode.Unauthorized, login("org2", assertion).status)
        assertEquals(HttpStatusCode.OK, login("org1", assertion).status, "still unused at org1")
    }
}
