package id.walt.ktorauthnz.tenants

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
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.InMemoryAccountStore
import id.walt.ktorauthnz.accounts.identifiers.methods.UsernameIdentifier
import id.walt.ktorauthnz.attempts.AttemptLimits
import id.walt.ktorauthnz.auth.ktorAuthnz
import id.walt.ktorauthnz.enrollment.passwordReset
import id.walt.ktorauthnz.ephemeral.InMemoryExpiringStore
import id.walt.ktorauthnz.examples.multitenant.ExampleIdentityProvider
import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.flows.authFlows
import id.walt.ktorauthnz.methods.OIDC
import id.walt.ktorauthnz.methods.UserPass
import id.walt.ktorauthnz.methods.storeddata.UserPassStoredData
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import id.walt.ktorauthnz.sessions.AuthSessionNextStepRedirectData
import id.walt.ktorauthnz.sessions.InMemorySessionStore
import id.walt.ktorauthnz.tokens.RefreshTokenSettings
import id.walt.ktorauthnz.tokens.TokenHandler
import id.walt.ktorauthnz.tokens.jwttoken.JwtTokenHandler
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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/** What an [authnzTenant] scope keeps apart between tenants, beyond sessions and accounts. */
class TenantScopeTest {

    private val accounts = InMemoryAccountStore()
    private val idp = ExampleIdentityProvider(clientId = "app")
    private val sentResets = mutableMapOf<String, String>()
    private val json = Json { ignoreUnknownKeys = true }

    init {
        runBlocking {
            for (tenant in listOf("t1", "t2")) inAuthnzTenant(tenant) {
                accounts.addAccount(UsernameIdentifier("alice"), identifierData = mapOf(UserPass.id to UserPassStoredData("$tenant-password")))
            }
        }
    }

    @AfterTest
    fun tearDown() {
        idp.close()
        KtorAuthnzManager.refreshTokens = null
    }

    private fun flowsOf(tenant: String) = listOf(
        AuthFlow.fromConfig("""{"method": "userpass", "success": true}"""),
        AuthFlow.fromConfig(
            """{"method": "oidc", "success": true, "config": {"openIdConfigurationUrl": "${idp.discoveryUrl}",
                "clientId": "app", "clientSecret": "s", "callbackUri": "http://localhost/$tenant/auth/oidc/callback"}}"""
        ),
    )

    private fun tenantTest(tokens: TokenHandler = KtorAuthNzTokenHandler(), block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(KtorAuthnz) {
                accountStore = accounts
                sessionStore = InMemorySessionStore()
                tokenHandler = tokens
                expiringStore = InMemoryExpiringStore()
                attemptLimits = AttemptLimits(maxFailuresPerIdentifier = 3, identifierWindow = 5.minutes)
                refreshTokens = RefreshTokenSettings(refreshTokenLifetime = 1.days)
            }
            install(Authentication) { ktorAuthnz("authnz") }
            install(ContentNegotiation) { json() }
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    val status = (cause as? HttpStatusError)?.status?.let(HttpStatusCode::fromValue)
                        ?: if (cause is IllegalArgumentException) HttpStatusCode.BadRequest else HttpStatusCode.InternalServerError
                    call.respondText("${cause::class.simpleName}: ${cause.message}", status = status)
                }
            }
            routing {
                route("{tenant}") {
                    authnzTenant { parameters["tenant"]!! }
                    route("auth") { authFlows(methods = listOf(UserPass, OIDC), flowsFor = { flowsOf(authnzTenant!!) }) }
                    passwordReset(method = UserPass) { name, token -> sentResets[name] = token }
                    tokenRefresh()
                    authenticate("authnz") { get("me") { call.respondText("ok") } }
                }
            }
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.post(path: String, body: String) =
        client.post(path) { contentType(ContentType.Application.Json); setBody(body) }

    private suspend fun ApplicationTestBuilder.login(tenant: String) =
        post("/$tenant/auth/userpass", """{"username": "alice", "password": "$tenant-password"}""")

    private fun HttpResponse.info(): AuthSessionInformation = runBlocking {
        assertEquals(HttpStatusCode.OK, status, bodyAsText()); json.decodeFromString(bodyAsText())
    }

    @Test
    fun `failed attempts lock a login name in its tenant only`() = tenantTest {
        repeat(3) { assertEquals(HttpStatusCode.Unauthorized, post("/t1/auth/userpass", """{"username": "alice", "password": "wrong"}""").status) }
        assertEquals(HttpStatusCode.TooManyRequests, login("t1").status)
        assertEquals(HttpStatusCode.OK, login("t2").status)
    }

    @Test
    fun `a password reset token confirms in its own tenant only`() = tenantTest {
        assertEquals(HttpStatusCode.Accepted, post("/t1/password/reset/request", """{"username": "alice"}""").status)
        val token = assertNotNull(sentResets["alice"])
        val confirm = """{"token": "$token", "new_password": "new-password-1"}"""

        assertEquals(HttpStatusCode.BadRequest, post("/t2/password/reset/confirm", confirm).status)
        assertEquals(HttpStatusCode.OK, login("t2").status, "t2's alice keeps her password")
        assertEquals(HttpStatusCode.NoContent, post("/t1/password/reset/confirm", confirm).status)
    }

    @Test
    fun `a refresh token refreshes in its own tenant only`() = tenantTest {
        val refreshToken = assertNotNull(login("t1").info().refreshToken)
        val body = """{"refresh_token": "$refreshToken"}"""
        assertEquals(HttpStatusCode.Unauthorized, post("/t2/token/refresh", body).status)
        assertEquals(HttpStatusCode.OK, post("/t1/token/refresh", body).status, "not used up by the refused attempt")
    }

    @Test
    fun `JWT login tokens name their tenant`() {
        val key = runBlocking {
            CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
                GenerateSoftwareKeyRequest(KeyId("login-key"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY))
            )
        }
        tenantTest(tokens = JwtTokenHandler(key, algorithm = JwsAlgorithm.ES256)) {
            val token = assertNotNull(login("t1").info().token)
            assertEquals(HttpStatusCode.OK, client.get("/t1/me") { bearerAuth(token) }.status)
            assertEquals(HttpStatusCode.Unauthorized, client.get("/t2/me") { bearerAuth(token) }.status)
        }
    }

    @Test
    fun `an OIDC callback at another tenant does not log in there`() = tenantTest {
        val started = client.get("/t1/auth/oidc/auth").info()
        val authorizationUrl = (started.nextStep as AuthSessionNextStepRedirectData).url
        idp.nonce = assertNotNull(authorizationUrl.parameters["nonce"])
        val state = authorizationUrl.parameters["state"]

        assertEquals(HttpStatusCode.NotFound, client.get("/t2/auth/oidc/callback?code=c&state=$state").status)
        assertEquals(HttpStatusCode.OK, client.get("/t1/auth/oidc/callback?code=c&state=$state").status)
    }
}
