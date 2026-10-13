package id.walt.ktorauthnz.enrollment

import com.atlassian.onetime.core.TOTPGenerator
import com.atlassian.onetime.model.TOTPSecret
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.errors.HttpStatusError
import id.walt.ktorauthnz.KtorAuthnz
import id.walt.ktorauthnz.accounts.InMemoryAccountStore
import id.walt.ktorauthnz.accounts.identifiers.methods.UsernameIdentifier
import id.walt.ktorauthnz.attempts.AttemptLimits
import id.walt.ktorauthnz.auth.authnzPrincipal
import id.walt.ktorauthnz.auth.ktorAuthnz
import id.walt.ktorauthnz.ephemeral.InMemoryExpiringStore
import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.flows.authFlows
import id.walt.ktorauthnz.methods.TOTP
import id.walt.ktorauthnz.methods.UserPass
import id.walt.ktorauthnz.methods.storeddata.TOTPStoredData
import id.walt.ktorauthnz.methods.storeddata.UserPassStoredData
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import id.walt.ktorauthnz.sessions.InMemorySessionStore
import id.walt.ktorauthnz.tokens.TokenHandler
import id.walt.ktorauthnz.tokens.jwttoken.JwtTokenHandler
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
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes

class AccountRoutesTest {

    private val accounts = InMemoryAccountStore()
    private val json = Json { ignoreUnknownKeys = true }
    private val secret = "JBSWY3DPEHPK3PXP"
    private val alice = runBlocking {
        accounts.addAccount(
            UsernameIdentifier("alice"),
            identifierData = mapOf(UserPass.id to UserPassStoredData("alice-password")),
            accountData = mapOf(TOTP.id to TOTPStoredData(secret)),
        )
    }
    private val bob = runBlocking {
        accounts.addAccount(UsernameIdentifier("bob"), identifierData = mapOf(UserPass.id to UserPassStoredData("bob-password")))
    }

    private fun accountTest(tokens: TokenHandler = KtorAuthNzTokenHandler(), block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(KtorAuthnz) {
                accountStore = accounts
                sessionStore = InMemorySessionStore()
                tokenHandler = tokens
                expiringStore = InMemoryExpiringStore()
                attemptLimits = AttemptLimits.DISABLED
            }
            install(Authentication) { ktorAuthnz("login") }
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
                    authFlows(listOf(
                        AuthFlow.fromConfig("""{"method": "userpass", "continue": [{"method": "totp", "success": true}]}"""),
                    ))
                }
                route("simple") { authFlows(listOf(AuthFlow.fromConfig("""{"method": "userpass", "success": true}"""))) }
                authenticate("login") {
                    accountRoutes()
                    get("me") {
                        val p = call.authnzPrincipal()!!
                        call.respondText("${p.accountId} ${p.methods.joinToString(",")} ${p.authenticatedAt != null}")
                    }
                    post("sensitive") {
                        call.requireRecentLogin(5.minutes, anyOfMethods = setOf("totp"))
                        call.respondText("done")
                    }
                }
            }
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.post(path: String, body: String? = null, token: String? = null) = client.post(path) {
        token?.let { bearerAuth(it) }
        if (body != null) { contentType(ContentType.Application.Json); setBody(body) }
    }

    private fun HttpResponse.info(): AuthSessionInformation = runBlocking {
        assertEquals(HttpStatusCode.OK, status, bodyAsText()); json.decodeFromString(bodyAsText())
    }

    private fun totp() = """{"code": "${TOTPGenerator().generateCurrent(TOTPSecret.fromBase32EncodedString(secret)).value}"}"""

    /** Alice with password, then TOTP. */
    private suspend fun ApplicationTestBuilder.loginAlice(): String {
        val first = post("/auth/userpass", """{"username": "alice", "password": "alice-password"}""").info()
        return assertNotNull(post("/auth/${first.id}/totp", totp()).info().token)
    }

    private suspend fun ApplicationTestBuilder.loginSimple(user: String) =
        assertNotNull(post("/simple/userpass", """{"username": "$user", "password": "$user-password"}""").info().token)

    private suspend fun ApplicationTestBuilder.sessions(token: String): List<AccountSession> =
        json.decodeFromString(client.get("/sessions") { bearerAuth(token) }.bodyAsText())

    @Test
    fun `the principal tells when and how the user logged in`() = accountTest {
        assertEquals("$alice userpass,totp true", client.get("/me") { bearerAuth(loginAlice()) }.bodyAsText())
    }

    @Test
    fun `JWT logins carry auth_time and amr`() {
        val key = runBlocking {
            CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
                GenerateSoftwareKeyRequest(KeyId("k"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY))
            )
        }
        accountTest(tokens = JwtTokenHandler(key, algorithm = JwsAlgorithm.ES256)) {
            assertEquals("$alice userpass,totp true", client.get("/me") { bearerAuth(loginAlice()) }.bodyAsText())
        }
    }

    @Test
    fun `a sensitive action needs a recent login with the required method`() = accountTest {
        assertEquals(HttpStatusCode.OK, post("/sensitive", token = loginAlice()).status)
        assertEquals(HttpStatusCode.Unauthorized, post("/sensitive", token = loginSimple("alice")).status, "no TOTP in this login")
    }

    @Test
    fun `a login older than the limit, or of unknown age, needs a new one`() {
        val loggedIn = kotlin.time.Instant.parse("2026-10-05T12:00:00Z")
        val principal = id.walt.ktorauthnz.auth.KtorAuthnzPrincipal("t", "a", "s", authenticatedAt = loggedIn, methods = listOf("email"))
        checkRecentLogin(principal, 5.minutes, emptySet(), loggedIn + 4.minutes)
        assertFailsWith<id.walt.ktorauthnz.exceptions.ReauthenticationRequiredException> {
            checkRecentLogin(principal, 5.minutes, emptySet(), loggedIn + 6.minutes)
        }
        assertFailsWith<id.walt.ktorauthnz.exceptions.ReauthenticationRequiredException> {
            checkRecentLogin(principal.copy(authenticatedAt = null), 5.minutes, emptySet(), loggedIn)
        }
    }

    @Test
    fun `logout ends the session and its token`() = accountTest {
        val token = loginAlice()
        assertEquals(HttpStatusCode.NoContent, post("/logout", token = token).status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/me") { bearerAuth(token) }.status)
    }

    @Test
    fun `an account lists its own sessions and ends one of them`() = accountTest {
        val laptop = loginAlice()
        val phone = loginSimple("alice")
        loginSimple("bob")

        val listed = sessions(laptop)
        assertEquals(2, listed.size)
        assertEquals(listOf(true), listed.filter { it.current }.map { it.current })
        val phoneSession = listed.single { !it.current }
        assertEquals(listOf("userpass"), phoneSession.methods)

        assertEquals(HttpStatusCode.NoContent, client.delete("/sessions/${phoneSession.sessionId}") { bearerAuth(laptop) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/me") { bearerAuth(phone) }.status)
        assertEquals(HttpStatusCode.OK, client.get("/me") { bearerAuth(laptop) }.status)
    }

    @Test
    fun `another account's session cannot be ended`() = accountTest {
        val aliceToken = loginAlice()
        val bobToken = loginSimple("bob")
        val bobSession = sessions(bobToken).single().sessionId
        assertEquals(HttpStatusCode.NotFound, client.delete("/sessions/$bobSession") { bearerAuth(aliceToken) }.status)
        assertEquals(HttpStatusCode.OK, client.get("/me") { bearerAuth(bobToken) }.status)
    }

    @Test
    fun `ending all sessions logs out everywhere`() = accountTest {
        val laptop = loginAlice()
        val phone = loginSimple("alice")
        assertEquals(HttpStatusCode.NoContent, client.delete("/sessions") { bearerAuth(laptop) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/me") { bearerAuth(laptop) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/me") { bearerAuth(phone) }.status)
    }
}
