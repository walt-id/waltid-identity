package id.walt.ktorauthnz.methods

import id.walt.ktorauthnz.KtorAuthnz
import id.walt.ktorauthnz.accounts.InMemoryAccountStore
import id.walt.ktorauthnz.ephemeral.InMemoryExpiringStore
import id.walt.ktorauthnz.examples.services.ExampleIdentityProvider
import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.flows.authFlows
import id.walt.ktorauthnz.sessions.AuthSessionNextStepRedirectData
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
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import kotlinx.serialization.json.Json
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/** How the identity provider's signing keys are fetched: once, again for a new key id, but not for every unknown one. */
class OidcProviderKeysTest {

    private val idp = ExampleIdentityProvider(clientId = "app")
    private val json = Json { ignoreUnknownKeys = true }

    private var now = Clock.System.now()

    @BeforeTest
    fun clear() {
        OIDC.jwksCache.clear(); OIDC.configurationCache.clear()
        OIDC.jwksCache.clock = object : Clock { override fun now() = this@OidcProviderKeysTest.now }
    }

    @AfterTest
    fun stop() {
        idp.close()
        OIDC.jwksCache.clock = Clock.System
    }

    private fun oidcTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val flow = AuthFlow.fromConfig(
            """{"method": "oidc", "success": true, "config": {"openIdConfigurationUrl": "${idp.discoveryUrl}",
                "clientId": "app", "clientSecret": "s", "callbackUri": "http://localhost/auth/oidc/callback"}}"""
        )
        application {
            install(KtorAuthnz) {
                accountStore = InMemoryAccountStore()
                sessionStore = InMemorySessionStore()
                tokenHandler = KtorAuthNzTokenHandler()
                expiringStore = InMemoryExpiringStore()
            }
            install(ContentNegotiation) { json() }
            install(StatusPages) {
                exception<Throwable> { call, cause -> call.respondText(cause.message ?: "", status = HttpStatusCode.InternalServerError) }
            }
            routing { route("auth") { authFlows(listOf(flow)) } }
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.login(): HttpResponse {
        val started = json.decodeFromString<AuthSessionInformation>(client.get("/auth/oidc/auth").bodyAsText())
        val url = (started.nextStep as AuthSessionNextStepRedirectData).url
        idp.nonce = url.parameters["nonce"]!!
        return client.get("/auth/oidc/callback?code=c&state=${url.parameters["state"]}")
    }

    @Test
    fun `keys are fetched once for many logins`() = oidcTest {
        repeat(3) { assertEquals(HttpStatusCode.OK, login().status) }
        assertEquals(1, idp.jwksRequests.get())
    }

    @Test
    fun `a rotated key is fetched when tokens name it`() = oidcTest {
        assertEquals(HttpStatusCode.OK, login().status)
        idp.rotateKey("idp-key-2")
        // Keys are looked up again at most once a minute; providers publish new keys before using them.
        now += 61.seconds
        assertEquals(HttpStatusCode.OK, login().status)
        assertEquals(2, idp.jwksRequests.get())
    }

    @Test
    fun `tokens naming unknown keys do not fetch the keys each time`() = oidcTest {
        assertEquals(HttpStatusCode.OK, login().status)
        idp.signedKid = "unknown-key"
        repeat(5) { assertNotEquals(HttpStatusCode.OK, login().status) }
        assertTrue(idp.jwksRequests.get() <= 2, "fetched ${idp.jwksRequests.get()} times")
    }
}
