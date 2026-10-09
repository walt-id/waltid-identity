package id.walt.ktorauthnz.methods

import id.walt.errors.HttpStatusError
import id.walt.ktorauthnz.KtorAuthnz
import id.walt.ktorauthnz.accounts.InMemoryAccountStore
import id.walt.ktorauthnz.accounts.identifiers.methods.EmailIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.UsernameIdentifier
import id.walt.ktorauthnz.attempts.AttemptLimits
import id.walt.ktorauthnz.ephemeral.InMemoryExpiringStore
import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.flows.authFlows
import id.walt.ktorauthnz.methods.config.EmailCodeDelivery
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class EmailCodeTest {

    private val accounts = InMemoryAccountStore()
    private val sent = mutableListOf<EmailCodeDelivery>()
    private val json = Json { ignoreUnknownKeys = true }
    private val bob = runBlocking {
        accounts.addAccount(
            UsernameIdentifier("bob"), EmailIdentifier("bob@example.com"),
            identifierData = mapOf(UserPass.id to UserPassStoredData("bob-password")),
        )
    }

    private val flows = listOf(
        AuthFlow.fromConfig("""{"method": "userpass", "continue": [{"method": "email-code", "success": true}]}"""),
        AuthFlow.fromConfig("""{"method": "email-code", "success": true}"""),
    )

    @OptIn(ExperimentalUuidApi::class)
    private fun emailTest(
        lifetime: Duration = 10.minutes,
        maxSends: Int = 5,
        registers: Boolean = false,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application {
            install(KtorAuthnz) {
                accountStore = accounts
                sessionStore = InMemorySessionStore()
                tokenHandler = KtorAuthNzTokenHandler()
                expiringStore = InMemoryExpiringStore()
                attemptLimits = AttemptLimits()
                emailCodes = EmailCodeSettings(codeLifetime = lifetime, maxSends = maxSends) { sent += it }
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
                    authFlows(flows) {
                        if (registers) registerUnknownAccounts(EmailCode) { accounts.addAccountIdentifierToAccount(Uuid.random().toString(), it) }
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

    private suspend fun ApplicationTestBuilder.afterPassword(): String {
        val first = post("/auth/userpass", """{"username": "bob", "password": "bob-password"}""").info()
        post("/auth/${first.id}/email-code/send").info()
        return first.id
    }

    @Test
    fun `as a later step, the code goes to the account and completes the login`() = emailTest {
        val session = afterPassword()
        val delivery = sent.single()
        assertEquals(bob, delivery.accountId)
        assertNull(delivery.email, "the application looks up the account's address")
        assertEquals(6, delivery.code.length)

        assertEquals(HttpStatusCode.Unauthorized, post("/auth/$session/email-code", """{"code": "000000x"}""").status)
        assertEquals(AuthSessionStatus.SUCCESS, post("/auth/$session/email-code", """{"code": "${delivery.code}"}""").info().status)
    }

    @Test
    fun `a code expires`() = emailTest(lifetime = 50.milliseconds) {
        val session = afterPassword()
        delay(100)
        assertEquals(HttpStatusCode.Unauthorized, post("/auth/$session/email-code", """{"code": "${sent.single().code}"}""").status)
    }

    @Test
    fun `a new code replaces the previous one`() = emailTest {
        val session = afterPassword()
        post("/auth/$session/email-code/send").info()
        val (old, new) = sent.map { it.code }
        if (old != new) assertEquals(HttpStatusCode.Unauthorized, post("/auth/$session/email-code", """{"code": "$old"}""").status)
        assertEquals(AuthSessionStatus.SUCCESS, post("/auth/$session/email-code", """{"code": "$new"}""").info().status)
    }

    @Test
    fun `codes sent are limited`() = emailTest(maxSends = 2) {
        val session = afterPassword()
        post("/auth/$session/email-code/send").info()
        assertEquals(HttpStatusCode.TooManyRequests, post("/auth/$session/email-code/send").status)
    }

    @Test
    fun `as the first step, it logs in by address without a password`() = emailTest {
        val started = post("/auth/email-code/send", """{"email": "bob@example.com"}""").info()
        assertEquals("bob@example.com", sent.single().email)
        val done = post("/auth/${started.id}/email-code", """{"code": "${sent.single().code}"}""").info()
        assertEquals(AuthSessionStatus.SUCCESS, done.status)
    }

    @Test
    fun `an unknown address gets the same answer, but no code`() = emailTest {
        val known = post("/auth/email-code/send", """{"email": "bob@example.com"}""")
        val unknown = post("/auth/email-code/send", """{"email": "nobody@example.com"}""")
        assertEquals(known.status, unknown.status)
        assertEquals(known.info().nextStepDescription?.replace(known.info().id, ""), unknown.info().nextStepDescription?.replace(unknown.info().id, ""))
        assertEquals(listOf("bob@example.com"), sent.map { it.email })
        assertEquals(HttpStatusCode.Unauthorized, post("/auth/${unknown.info().id}/email-code", """{"code": "123456"}""").status)
    }

    @Test
    fun `with registration, an unknown address signs up once its code is entered`() = emailTest(registers = true) {
        val started = post("/auth/email-code/send", """{"email": "new@example.com"}""").info()
        assertNull(accounts.lookupAccountUuid(EmailIdentifier("new@example.com")), "not before the code is entered")
        assertEquals(AuthSessionStatus.SUCCESS, post("/auth/${started.id}/email-code", """{"code": "${sent.single().code}"}""").info().status)
        assertNotNull(accounts.lookupAccountUuid(EmailIdentifier("new@example.com")))
    }
}
