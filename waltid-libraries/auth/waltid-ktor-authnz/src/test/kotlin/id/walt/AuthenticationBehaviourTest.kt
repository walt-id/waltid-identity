package id.walt

import com.atlassian.onetime.core.TOTPGenerator
import com.atlassian.onetime.model.TOTPSecret
import id.walt.errors.StatusException
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.attempts.AttemptLimits
import id.walt.ktorauthnz.ephemeral.InMemoryExpiringStore
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import id.walt.ktorauthnz.sessions.AuthSessionStatus
import id.walt.ktorauthnz.sessions.InMemorySessionStore
import id.walt.ktorauthnz.sessions.SessionManager
import id.walt.ktorauthnz.sessions.SessionTokenCookieHandler
import id.walt.web.plugins.configureSerialization
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Authentication behaviour over HTTP, against the library's example app (`testApp`). */
class AuthenticationBehaviourTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val alice = "11111111-1111-1111-1111-000000000000"

    @BeforeTest
    fun freshState() {
        KtorAuthnzManager.sessionStore = InMemorySessionStore()
        KtorAuthnzManager.expiringStore = InMemoryExpiringStore()
        KtorAuthnzManager.attemptLimits = AttemptLimits()
    }

    @AfterTest
    fun restore() {
        SessionTokenCookieHandler.cookieName = "ktor-authnz-auth"
        KtorAuthnzManager.attemptLimits = AttemptLimits()
    }

    private fun Application.module(jwt: Boolean) {
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                val status = when (cause) {
                    is id.walt.errors.HttpStatusError -> HttpStatusCode.fromValue(cause.status)
                    is IllegalArgumentException -> HttpStatusCode.BadRequest
                    else -> HttpStatusCode.InternalServerError
                }
                call.respondText("${cause::class.simpleName}: ${cause.message}", status = status)
            }
        }
        configureSerialization()
        testApp(jwt)
    }

    private fun test(jwt: Boolean = false, block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { module(jwt) }
        block()
    }

    private suspend fun ApplicationTestBuilder.login(user: String = "alice1", password: String = "123456") =
        client.post("/auth/flows/global-implicit1/userpass") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"$user","password":"$password"}""")
        }

    private suspend fun ApplicationTestBuilder.token() =
        assertNotNull(json.decodeFromString<AuthSessionInformation>(login().bodyAsText()).token)

    private suspend fun ApplicationTestBuilder.protectedStatus(token: String) =
        client.get("/protected") { bearerAuth(token) }.status

    /** An explicit two-step session that passed its password step and waits for TOTP. */
    private suspend fun ApplicationTestBuilder.sessionAwaitingTotp(): String {
        val start = json.decodeFromString<AuthSessionInformation>(client.post("/auth/flows/global-explicit2/start").bodyAsText())
        client.post("/auth/flows/global-explicit2/${start.id}/userpass") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"alice1","password":"123456"}""")
        }
        return start.id
    }

    private suspend fun ApplicationTestBuilder.totp(sessionId: String, code: String) =
        client.post("/auth/flows/global-explicit2/$sessionId/totp") {
            contentType(ContentType.Application.Json)
            setBody("""{"code":"$code"}""")
        }

    private fun currentTotp() =
        TOTPGenerator().generateCurrent(TOTPSecret.fromBase32EncodedString("JBSWY3DPEHPK3PXP")).value

    @Test
    fun `wrong TOTP codes use up the session`() = test {
        val sessionId = sessionAwaitingTotp()
        val statuses = (1..5).map { totp(sessionId, "000000").status }
        assertEquals(List(5) { HttpStatusCode.Unauthorized }, statuses)

        assertEquals(HttpStatusCode.TooManyRequests, totp(sessionId, currentTotp()).status, "the right code comes too late")
        assertEquals(AuthSessionStatus.FAILURE, SessionManager.getSessionById(sessionId).status)
    }

    @Test
    fun `wrong passwords lock the username across sessions, not other users`() = test {
        repeat(10) { assertEquals(HttpStatusCode.Unauthorized, login(password = "wrong").status) }

        assertEquals(HttpStatusCode.TooManyRequests, login().status, "locked even with the right password")
        assertEquals(HttpStatusCode.TooManyRequests, login(user = "ALICE1").status, "case does not escape the lock")
        assertEquals(HttpStatusCode.NotFound, login(user = "bob").status, "other identifiers are not locked")
    }

    @Test
    fun `a successful login clears the failed attempts of the username`() = test {
        repeat(9) { login(password = "wrong") }
        assertEquals(HttpStatusCode.OK, login().status)
        repeat(9) { login(password = "wrong") }
        assertEquals(HttpStatusCode.OK, login().status, "the count restarted after the success")
    }

    @Test
    fun `failed first steps store no session`() = test {
        val sessions = KtorAuthnzManager.sessionStore as InMemorySessionStore
        repeat(5) { login(password = "wrong") }
        assertEquals(0, sessions.sessions.size)
        login()
        assertEquals(1, sessions.sessions.size)
    }

    @Test
    fun `opaque tokens end with logout and with revoking the account's sessions`() = test {
        val loggedOut = token()
        assertEquals(HttpStatusCode.OK, protectedStatus(loggedOut))
        client.get("/logout") { bearerAuth(loggedOut) }
        assertEquals(HttpStatusCode.Unauthorized, protectedStatus(loggedOut))

        val revoked = token()
        SessionManager.invalidateAllSessionsForAccount(alice)
        assertEquals(HttpStatusCode.Unauthorized, protectedStatus(revoked))
    }

    @Test
    fun `JWTs stay valid after logout unless the session is required`() = test(jwt = true) {
        val token = token()
        client.get("/logout") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, protectedStatus(token), "stateless by default")

        (KtorAuthnzManager.tokenHandler as id.walt.ktorauthnz.tokens.jwttoken.JwtTokenHandler).requireActiveSession = true
        assertEquals(HttpStatusCode.Unauthorized, protectedStatus(token))
        val fresh = token()
        assertEquals(HttpStatusCode.OK, protectedStatus(fresh))
    }

    @Test
    fun `a finished, unknown or mismatched session is a client error`() = test {
        val sessionId = sessionAwaitingTotp()
        assertEquals(HttpStatusCode.OK, totp(sessionId, currentTotp()).status)

        assertEquals(HttpStatusCode.BadRequest, totp(sessionId, currentTotp()).status, "already complete")
        assertEquals(HttpStatusCode.NotFound, totp("no-such-session", currentTotp()).status)

        val waitingForPassword = json.decodeFromString<AuthSessionInformation>(
            client.post("/auth/flows/global-explicit2/start").bodyAsText()
        ).id
        assertEquals(HttpStatusCode.BadRequest, totp(waitingForPassword, currentTotp()).status, "expects userpass first")
    }

    @Test
    fun `a renamed session cookie is read back`() = test {
        SessionTokenCookieHandler.cookieName = "my-app-auth"
        val response = login()
        assertTrue(response.headers.getAll(HttpHeaders.SetCookie).orEmpty().any { it.startsWith("my-app-auth=") })
        val token = assertNotNull(json.decodeFromString<AuthSessionInformation>(response.bodyAsText()).token)

        val viaCookie = client.get("/protected") { header(HttpHeaders.Cookie, "my-app-auth=$token") }
        assertEquals(HttpStatusCode.OK, viaCookie.status)
    }
}
