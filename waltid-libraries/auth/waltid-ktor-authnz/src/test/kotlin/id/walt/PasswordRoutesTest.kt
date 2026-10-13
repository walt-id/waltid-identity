package id.walt

import id.walt.ktorauthnz.KtorAuthnz
import id.walt.ktorauthnz.accounts.ExampleAccountStore
import id.walt.ktorauthnz.accounts.identifiers.methods.EmailIdentifier
import id.walt.ktorauthnz.attempts.AttemptLimits
import id.walt.ktorauthnz.auth.ktorAuthnz
import id.walt.ktorauthnz.enrollment.passwordChange
import id.walt.ktorauthnz.enrollment.passwordReset
import id.walt.ktorauthnz.ephemeral.InMemoryExpiringStore
import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.flows.authFlows
import id.walt.ktorauthnz.methods.EmailPass
import id.walt.ktorauthnz.methods.storeddata.EmailPassStoredData
import id.walt.ktorauthnz.sessions.AuthSessionInformation
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
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

class PasswordRoutesTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val email = "dave-${Uuid.random()}@example.com"
    private val sentTokens = mutableListOf<Pair<String, String>>()

    init {
        runBlocking {
            ExampleAccountStore.addAccountIdentifierToAccount(Uuid.random().toString(), EmailIdentifier(email))
            ExampleAccountStore.addAccountIdentifierStoredData(EmailIdentifier(email), EmailPass.id, EmailPassStoredData(password = "first-password"))
        }
    }

    private fun passwordTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(KtorAuthnz) {
                accountStore = ExampleAccountStore
                sessionStore = InMemorySessionStore()
                tokenHandler = KtorAuthNzTokenHandler()
                expiringStore = InMemoryExpiringStore()
                attemptLimits = AttemptLimits()
            }
            install(Authentication) { ktorAuthnz("authnz") }
            install(ContentNegotiation) { json() }
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    val status = (cause as? id.walt.errors.HttpStatusError)?.status?.let(HttpStatusCode::fromValue)
                        ?: if (cause is IllegalArgumentException) HttpStatusCode.BadRequest else HttpStatusCode.InternalServerError
                    call.respondText("${cause::class.simpleName}: ${cause.message}", status = status)
                }
            }
            routing {
                route("login") { authFlows(listOf(AuthFlow(method = "email", success = true))) }
                passwordReset { name, token -> sentTokens += name to token }
                authenticate("authnz") {
                    passwordChange()
                    get("/me") { call.respondText("ok") }
                }
            }
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.post(path: String, body: String, token: String? = null) =
        client.post(path) { token?.let { bearerAuth(it) }; contentType(ContentType.Application.Json); setBody(body) }

    private suspend fun ApplicationTestBuilder.login(password: String) = post("/login/email", """{"email":"$email","password":"$password"}""")

    private suspend fun ApplicationTestBuilder.token(password: String) =
        assertNotNull(json.decodeFromString<AuthSessionInformation>(login(password).bodyAsText()).token)

    @Test
    fun `a password change needs the current password and ends every session`() = passwordTest {
        val other = token("first-password")
        val current = token("first-password")

        val wrong = post("/password/change", """{"email":"$email","current_password":"nope","new_password":"second-password"}""", current)
        assertEquals(HttpStatusCode.Unauthorized, wrong.status)
        val short = post("/password/change", """{"email":"$email","current_password":"first-password","new_password":"short"}""", current)
        assertEquals(HttpStatusCode.BadRequest, short.status)

        val changed = post("/password/change", """{"email":"$email","current_password":"first-password","new_password":"second-password"}""", current)
        assertEquals(HttpStatusCode.NoContent, changed.status, changed.bodyAsText())
        assertEquals(HttpStatusCode.Unauthorized, client.get("/me") { bearerAuth(other) }.status)
        assertEquals(HttpStatusCode.Unauthorized, login("first-password").status)
        assertEquals(HttpStatusCode.OK, login("second-password").status)
    }

    @Test
    fun `a reset token sets a new password once`() = passwordTest {
        val session = token("first-password")
        assertEquals(HttpStatusCode.Accepted, post("/password/reset/request", """{"email":"nobody@example.com"}""").status)
        assertEquals(emptyList(), sentTokens, "no token for unknown accounts")

        assertEquals(HttpStatusCode.Accepted, post("/password/reset/request", """{"email":"$email"}""").status)
        val (name, resetToken) = sentTokens.single()
        assertEquals(email, name)

        val confirm = """{"token":"$resetToken","new_password":"reset-password"}"""
        assertEquals(HttpStatusCode.NoContent, post("/password/reset/confirm", confirm).status)
        assertEquals(HttpStatusCode.BadRequest, post("/password/reset/confirm", confirm).status, "single use")
        assertEquals(HttpStatusCode.Unauthorized, client.get("/me") { bearerAuth(session) }.status)
        assertEquals(HttpStatusCode.OK, login("reset-password").status)
    }

    @Test
    fun `reset requests are limited per login`() = passwordTest {
        repeat(5) { assertEquals(HttpStatusCode.Accepted, post("/password/reset/request", """{"email":"$email"}""").status) }
        assertEquals(HttpStatusCode.TooManyRequests, post("/password/reset/request", """{"email":"$email"}""").status)
    }
}
