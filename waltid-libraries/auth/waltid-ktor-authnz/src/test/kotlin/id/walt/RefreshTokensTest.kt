package id.walt

import id.walt.ktorauthnz.KtorAuthnz
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.ExampleAccountStore
import id.walt.ktorauthnz.attempts.AttemptLimits
import id.walt.ktorauthnz.auth.ktorAuthnz
import id.walt.ktorauthnz.ephemeral.InMemoryExpiringStore
import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.flows.authFlows
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import id.walt.ktorauthnz.sessions.InMemorySessionStore
import id.walt.ktorauthnz.tokens.RefreshTokenSettings
import id.walt.ktorauthnz.tokens.ktorauthnztoken.KtorAuthNzTokenHandler
import id.walt.ktorauthnz.tokens.tokenRefresh
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
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

class RefreshTokensTest {

    private val json = Json { ignoreUnknownKeys = true }

    @AfterTest
    fun off() {
        KtorAuthnzManager.refreshTokens = null
    }

    private fun refreshTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(KtorAuthnz) {
                accountStore = ExampleAccountStore
                sessionStore = InMemorySessionStore()
                tokenHandler = KtorAuthNzTokenHandler()
                expiringStore = InMemoryExpiringStore()
                attemptLimits = AttemptLimits()
                refreshTokens = RefreshTokenSettings(accessTokenLifetime = 1.seconds, refreshTokenLifetime = 1.days)
            }
            install(Authentication) { ktorAuthnz("authnz") }
            install(ContentNegotiation) { json() }
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    val status = (cause as? id.walt.errors.HttpStatusError)?.status?.let(HttpStatusCode::fromValue) ?: HttpStatusCode.InternalServerError
                    call.respondText("${cause::class.simpleName}: ${cause.message}", status = status)
                }
            }
            routing {
                route("login") { authFlows(listOf(AuthFlow(method = "userpass", success = true))) }
                tokenRefresh()
                authenticate("authnz") { get("/me") { call.respondText("ok") } }
            }
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.info(response: HttpResponse) = json.decodeFromString<AuthSessionInformation>(response.bodyAsText())

    private suspend fun ApplicationTestBuilder.refresh(refreshToken: String) = client.post("/token/refresh") {
        contentType(ContentType.Application.Json); setBody("""{"refresh_token":"$refreshToken"}""")
    }

    private suspend fun ApplicationTestBuilder.me(token: String) = client.get("/me") { bearerAuth(token) }.status

    @Test
    fun `a short login token is renewed with a single-use refresh token`() = refreshTest {
        val login = info(client.post("/login/userpass") {
            contentType(ContentType.Application.Json); setBody("""{"username":"alice1","password":"123456"}""")
        })
        val token = assertNotNull(login.token)
        val refreshToken = assertNotNull(login.refreshToken)
        assertEquals(HttpStatusCode.OK, me(token))

        Thread.sleep(1200) // real time: expiry is checked against the system clock
        assertEquals(HttpStatusCode.Unauthorized, me(token), "the login token expired")

        val refreshed = info(refresh(refreshToken))
        val newToken = assertNotNull(refreshed.token)
        assertNotEquals(refreshToken, refreshed.refreshToken)
        assertEquals(HttpStatusCode.OK, me(newToken))

        assertEquals(HttpStatusCode.Unauthorized, refresh(refreshToken).status, "reused")
        assertEquals(HttpStatusCode.Unauthorized, me(newToken), "reuse ends the session")
        assertEquals(HttpStatusCode.Unauthorized, refresh(assertNotNull(refreshed.refreshToken)).status)
    }

    @Test
    fun `refreshing ends the previous login token`() = refreshTest {
        val login = info(client.post("/login/userpass") {
            contentType(ContentType.Application.Json); setBody("""{"username":"alice1","password":"123456"}""")
        })
        val token = assertNotNull(login.token)
        val refreshed = info(refresh(assertNotNull(login.refreshToken)))
        assertEquals(HttpStatusCode.Unauthorized, me(token))
        assertEquals(HttpStatusCode.OK, me(assertNotNull(refreshed.token)))
    }
}
