package id.walt.ktorauthnz.methods

import id.walt.errors.HttpStatusError
import id.walt.ktorauthnz.KtorAuthnz
import id.walt.ktorauthnz.accounts.InMemoryAccountStore
import id.walt.ktorauthnz.accounts.identifiers.methods.UsernameIdentifier
import id.walt.ktorauthnz.attempts.AttemptLimits
import id.walt.ktorauthnz.ephemeral.InMemoryExpiringStore
import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.flows.allMethods
import id.walt.ktorauthnz.flows.authFlows
import id.walt.ktorauthnz.methods.config.EmailCodeSettings
import id.walt.ktorauthnz.methods.storeddata.UserPassStoredData
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import id.walt.ktorauthnz.sessions.AuthSessionStatus
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
import kotlin.test.*

class IdentifyTest {

    private val accounts = InMemoryAccountStore()
    private val sent = mutableListOf<String>()
    private val json = Json { ignoreUnknownKeys = true }
    private val alice = runBlocking {
        accounts.addAccount(UsernameIdentifier("alice"), identifierData = mapOf(UserPass.id to UserPassStoredData("alice-password")))
    }

    /** Usernames; unknown email addresses may sign up with a code. */
    private val flow = AuthFlow.fromConfig(
        """{"method": "identify", "config": {
             "default": [{"method": "userpass", "success": true}],
             "unknown": [{"method": "email-code", "success": true}]}}"""
    )

    private fun identifyTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(KtorAuthnz) {
                accountStore = accounts
                sessionStore = InMemorySessionStore()
                tokenHandler = KtorAuthNzTokenHandler()
                expiringStore = InMemoryExpiringStore()
                attemptLimits = AttemptLimits()
                emailCodes = EmailCodeSettings { sent += it.code }
            }
            install(ContentNegotiation) { json() }
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    val status = (cause as? HttpStatusError)?.status?.let(HttpStatusCode::fromValue)
                        ?: if (cause is IllegalArgumentException) HttpStatusCode.BadRequest else HttpStatusCode.InternalServerError
                    call.respondText("${cause::class.simpleName}: ${cause.message}", status = status)
                }
            }
            routing {
                route("auth") {
                    // The methods the identify configuration names get routes without being listed.
                    authFlows(listOf(flow)) {
                        registerUnknownAccounts(EmailCode) { accounts.addAccountIdentifierToAccount("new-account", it) }
                    }
                }
            }
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.post(path: String, body: String? = null) = client.post(path) {
        if (body != null) { contentType(ContentType.Application.Json); setBody(body) }
    }

    private fun HttpResponse.info(): AuthSessionInformation = runBlocking {
        assertEquals(HttpStatusCode.OK, status, bodyAsText()); json.decodeFromString(bodyAsText())
    }

    @Test
    fun `the methods of the identify configuration are the flow's methods`() {
        assertEquals(setOf("identify", "userpass", "email-code"), flow.allMethods())
    }

    @Test
    fun `a username identifies, then only its password is needed`() = identifyTest {
        val session = post("/auth/identify", """{"username": "alice"}""").info()
        assertEquals(listOf("userpass"), session.nextMethod)
        assertEquals(HttpStatusCode.Unauthorized, post("/auth/${session.id}/userpass", """{"password": "wrong"}""").status)
        assertEquals(AuthSessionStatus.SUCCESS, post("/auth/${session.id}/userpass", """{"password": "alice-password"}""").info().status)
    }

    @Test
    fun `an unknown identifier follows the unknown flows, here a sign-up by email code`() = identifyTest {
        val session = post("/auth/identify", """{"email": "new@example.com"}""").info()
        assertEquals(listOf("email-code"), session.nextMethod)
        post("/auth/${session.id}/email-code/send").info()
        assertEquals(AuthSessionStatus.SUCCESS, post("/auth/${session.id}/email-code", """{"code": "${sent.single()}"}""").info().status)
    }

    @Test
    fun `identify needs an email or a username`() = identifyTest {
        assertEquals(HttpStatusCode.BadRequest, post("/auth/identify", """{"phone": "123"}""").status)
        assertEquals(HttpStatusCode.BadRequest, post("/auth/identify", """{"email": "not-an-address"}""").status)
    }
}
