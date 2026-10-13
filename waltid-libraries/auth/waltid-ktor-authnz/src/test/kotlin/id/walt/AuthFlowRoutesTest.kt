package id.walt

import com.atlassian.onetime.core.TOTPGenerator
import com.atlassian.onetime.model.TOTPSecret
import id.walt.errors.StatusException
import id.walt.ktorauthnz.KtorAuthnz
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.ExampleAccountStore
import id.walt.ktorauthnz.accounts.identifiers.methods.EmailIdentifier
import id.walt.ktorauthnz.attempts.AttemptLimits
import id.walt.ktorauthnz.auth.authnzPrincipal
import id.walt.ktorauthnz.auth.getAuthenticatedAccount
import id.walt.ktorauthnz.auth.ktorAuthnz
import id.walt.ktorauthnz.ephemeral.InMemoryExpiringStore
import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.flows.authFlows
import id.walt.ktorauthnz.methods.EmailPass
import id.walt.ktorauthnz.methods.storeddata.EmailPassStoredData
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import id.walt.ktorauthnz.sessions.AuthSessionStatus
import id.walt.ktorauthnz.sessions.InMemorySessionStore
import id.walt.ktorauthnz.tokens.ktorauthnztoken.KtorAuthNzTokenHandler
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AuthFlowRoutesTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val alice = "11111111-1111-1111-1111-000000000000"

    private val flows = listOf(
        AuthFlow.fromConfig("""{"method": "userpass", "continue": [{"method": "totp", "success": true}]}"""),
        AuthFlow.fromConfig("""{"method": "email", "success": true}"""),
    )

    init {
        runBlocking {
            val email = EmailIdentifier("alice@example.com")
            ExampleAccountStore.addAccountIdentifierToAccount(alice, email)
            ExampleAccountStore.addAccountIdentifierStoredData(email, EmailPass.id, EmailPassStoredData(password = "123456"))
        }
    }

    private fun flowTest(
        validate: suspend ApplicationCall.(id.walt.ktorauthnz.auth.KtorAuthnzPrincipal) -> Any? = { it },
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application {
            install(KtorAuthnz) {
                accountStore = ExampleAccountStore
                sessionStore = InMemorySessionStore()
                tokenHandler = KtorAuthNzTokenHandler()
                expiringStore = InMemoryExpiringStore()
                attemptLimits = AttemptLimits()
            }
            install(Authentication) { ktorAuthnz("authnz") { validate(validate) } }
            install(ContentNegotiation) { json() }
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    val status = (cause as? id.walt.errors.HttpStatusError)?.status?.let(HttpStatusCode::fromValue)
                        ?: if (cause is IllegalArgumentException) HttpStatusCode.BadRequest else HttpStatusCode.InternalServerError
                    call.respondText("${cause::class.simpleName}: ${cause.message}", status = status)
                }
            }
            routing {
                route("auth") { authFlows(flows) { tenant = { request.host() } } }
                authenticate("authnz") {
                    get("/me") {
                        call.respondText("${call.getAuthenticatedAccount()} ${call.authnzPrincipal()?.sessionId != null}")
                    }
                }
            }
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.post(path: String, body: String? = null, host: String = "localhost") =
        client.post(path) {
            header(HttpHeaders.Host, host)
            if (body != null) { contentType(ContentType.Application.Json); setBody(body) }
        }

    private fun HttpResponse.info() = runBlocking { json.decodeFromString<AuthSessionInformation>(bodyAsText()) }

    private val password = """{"username":"alice1","password":"123456"}"""
    private fun totp() = """{"code":"${TOTPGenerator().generateCurrent(TOTPSecret.fromBase32EncodedString("JBSWY3DPEHPK3PXP")).value}"}"""

    @Test
    fun `a multi-step flow is served from its definition, next_method naming the next URL`() = flowTest {
        val first = post("/auth/userpass", password).info()
        assertEquals(AuthSessionStatus.CONTINUE_NEXT_FLOW, first.status)
        val next = assertNotNull(first.nextMethod).single()

        val done = post("/auth/${first.id}/$next", totp())
        assertEquals(HttpStatusCode.OK, done.status, done.bodyAsText())
        val token = assertNotNull(done.info().token)

        assertEquals("$alice true", client.get("/me") { bearerAuth(token) }.bodyAsText())
    }

    @Test
    fun `each flow starts at its own first method`() = flowTest {
        val email = post("/auth/email", """{"email":"alice@example.com","password":"123456"}""")
        assertEquals(AuthSessionStatus.SUCCESS, email.info().status)
        assertEquals(HttpStatusCode.NotFound, post("/auth/totp", totp()).status, "no flow starts with totp: no route")
    }

    @Test
    fun `an explicitly started session names its flow`() = flowTest {
        assertEquals(HttpStatusCode.BadRequest, post("/auth/start").status, "several flows: which one?")
        val started = post("/auth/start?flow=userpass").info()
        assertEquals(listOf("userpass"), started.nextMethod)
        assertEquals(AuthSessionStatus.CONTINUE_NEXT_FLOW, post("/auth/${started.id}/userpass", password).info().status)
    }

    @Test
    fun `a session cannot be continued from another tenant`() = flowTest {
        val first = post("/auth/userpass", password, host = "tenant-a.example").info()
        assertEquals(HttpStatusCode.NotFound, post("/auth/${first.id}/totp", totp(), host = "tenant-b.example").status)
        assertEquals(HttpStatusCode.OK, post("/auth/${first.id}/totp", totp(), host = "tenant-a.example").status)
    }

    @Test
    fun `the provider hook can reject an authenticated caller`() = flowTest(validate = { null }) {
        val token = assertNotNull(post("/auth/email", """{"email":"alice@example.com","password":"123456"}""").info().token)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/me") { bearerAuth(token) }.status)
    }
}
