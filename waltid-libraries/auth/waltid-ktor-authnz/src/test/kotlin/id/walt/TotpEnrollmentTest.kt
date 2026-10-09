package id.walt

import com.atlassian.onetime.core.TOTPGenerator
import com.atlassian.onetime.model.TOTPSecret
import id.walt.ktorauthnz.KtorAuthnz
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.ExampleAccountStore
import id.walt.ktorauthnz.accounts.identifiers.methods.UsernameIdentifier
import id.walt.ktorauthnz.attempts.AttemptLimits
import id.walt.ktorauthnz.auth.ktorAuthnz
import id.walt.ktorauthnz.enrollment.RecoveryCodes
import id.walt.ktorauthnz.enrollment.TotpEnrollmentStart
import id.walt.ktorauthnz.enrollment.totpEnrollment
import id.walt.ktorauthnz.ephemeral.InMemoryExpiringStore
import id.walt.ktorauthnz.events.AuthnzEvent
import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.flows.authFlows
import id.walt.ktorauthnz.methods.RecoveryCode
import id.walt.ktorauthnz.methods.UserPass
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
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class TotpEnrollmentTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val events = mutableListOf<AuthnzEvent>()

    private val flow = AuthFlow.fromConfig(
        """{"method": "userpass", "continue": [{"method": "totp", "success": true}, {"method": "recovery-code", "success": true}]}"""
    )
    private val passwordOnly = AuthFlow.fromConfig("""{"method": "userpass", "success": true}""")

    /** A fresh account with a password and no second factor. */
    private val username = "carol-${Uuid.random()}"
    private val accountId = Uuid.random().toString()

    init {
        runBlocking {
            ExampleAccountStore.addAccountIdentifierToAccount(accountId, UsernameIdentifier(username))
            ExampleAccountStore.addAccountIdentifierStoredData(UsernameIdentifier(username), UserPass.id, UserPassStoredData(password = "secret-password"))
        }
    }

    private fun enrollmentTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(KtorAuthnz) {
                accountStore = ExampleAccountStore
                sessionStore = InMemorySessionStore()
                tokenHandler = KtorAuthNzTokenHandler()
                expiringStore = InMemoryExpiringStore()
                attemptLimits = AttemptLimits()
                onEvent { events += it }
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
                route("login") { authFlows(listOf(passwordOnly)) }
                route("mfa") { authFlows(listOf(flow)) }
                authenticate("authnz") { totpEnrollment(issuer = "walt.id test") }
            }
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.post(path: String, body: String? = null, token: String? = null) =
        client.post(path) {
            token?.let { bearerAuth(it) }
            if (body != null) { contentType(ContentType.Application.Json); setBody(body) }
        }

    private fun code(secret: String) = TOTPGenerator().generateCurrent(TOTPSecret.fromBase32EncodedString(secret)).value

    private suspend fun ApplicationTestBuilder.passwordToken() = assertNotNull(
        json.decodeFromString<AuthSessionInformation>(
            post("/login/userpass", """{"username":"$username","password":"secret-password"}""").bodyAsText()
        ).token
    )

    private suspend fun ApplicationTestBuilder.enroll(): Pair<String, List<String>> {
        val token = passwordToken()
        val start = json.decodeFromString<TotpEnrollmentStart>(post("/totp/enroll", token = token).bodyAsText())
        assertTrue(start.otpauthUri.startsWith("otpauth://totp/walt.id%20test:"))
        assertEquals(HttpStatusCode.Unauthorized, post("/totp/enroll/confirm", """{"code":"000000"}""", token).status)
        val confirmed = post("/totp/enroll/confirm", """{"code":"${code(start.secret)}"}""", token)
        assertEquals(HttpStatusCode.OK, confirmed.status, confirmed.bodyAsText())
        return start.secret to json.decodeFromString<RecoveryCodes>(confirmed.bodyAsText()).recoveryCodes
    }

    private suspend fun ApplicationTestBuilder.secondStep(method: String, code: String): HttpResponse {
        val first = json.decodeFromString<AuthSessionInformation>(
            post("/mfa/userpass", """{"username":"$username","password":"secret-password"}""").bodyAsText()
        )
        return post("/mfa/${first.id}/$method", """{"code":"$code"}""")
    }

    @Test
    fun `an enrolled TOTP logs in, and each code only once`() = enrollmentTest {
        val (secret, _) = enroll()
        assertTrue(events.any { it is AuthnzEvent.MethodEnrolled && it.accountId == accountId && it.method == "totp" })

        val code = code(secret)
        val login = secondStep("totp", code)
        assertEquals(AuthSessionStatus.SUCCESS, json.decodeFromString<AuthSessionInformation>(login.bodyAsText()).status)
        assertEquals(HttpStatusCode.Unauthorized, secondStep("totp", code).status, "replayed code")
    }

    @Test
    fun `a recovery code replaces TOTP once`() = enrollmentTest {
        val (_, recoveryCodes) = enroll()
        assertEquals(10, recoveryCodes.size)

        assertEquals(HttpStatusCode.OK, secondStep("recovery-code", recoveryCodes.first().uppercase()).status)
        assertEquals(HttpStatusCode.Unauthorized, secondStep("recovery-code", recoveryCodes.first()).status, "used up")
        assertEquals(HttpStatusCode.OK, secondStep("recovery-code", recoveryCodes.last()).status)
    }

    @Test
    fun `a recovery code stays used when its removal from the account is lost`() = enrollmentTest {
        val (_, recoveryCodes) = enroll()
        val unused = ExampleAccountStore.lookupStoredDataForAccount(accountId, RecoveryCode)
        assertEquals(HttpStatusCode.OK, secondStep("recovery-code", recoveryCodes.first()).status)

        // A concurrent request that read the codes before this use writes them back, the used one included.
        ExampleAccountStore.updateAccountStoredData(accountId, RecoveryCode.id, assertNotNull(unused))
        assertEquals(HttpStatusCode.Unauthorized, secondStep("recovery-code", recoveryCodes.first()).status, "used once already")
    }

    @Test
    fun `login events name the method and outcome`() = enrollmentTest {
        post("/login/userpass", """{"username":"$username","password":"wrong"}""")
        passwordToken()
        val failed = events.filterIsInstance<AuthnzEvent.LoginStepFailed>().single()
        assertEquals("userpass" to username, failed.method to failed.identifier)
        assertTrue(events.filterIsInstance<AuthnzEvent.LoginStepSucceeded>().single().completed)
    }
}
