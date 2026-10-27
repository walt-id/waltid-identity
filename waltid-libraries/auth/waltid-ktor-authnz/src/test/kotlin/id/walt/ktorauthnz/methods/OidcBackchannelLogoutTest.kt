package id.walt.ktorauthnz.methods

import id.walt.ktorauthnz.KtorAuthnz
import id.walt.ktorauthnz.accounts.InMemoryAccountStore
import id.walt.ktorauthnz.ephemeral.InMemoryExpiringStore
import id.walt.ktorauthnz.examples.services.ExampleIdentityProvider
import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.flows.authFlows
import id.walt.ktorauthnz.sessions.InMemorySessionStore
import id.walt.ktorauthnz.tokens.ktorauthnztoken.KtorAuthNzTokenHandler
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** What the identity provider's back-channel logout call is checked for before its logout token is looked at. */
class OidcBackchannelLogoutTest {

    private val idp = ExampleIdentityProvider(clientId = "app")

    @AfterTest
    fun stop() = idp.close()

    private fun backchannelTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
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
            install(StatusPages) {
                exception<Throwable> { call, cause -> call.respondText(cause.message ?: "", status = HttpStatusCode.InternalServerError) }
            }
            routing { route("auth") { authFlows(listOf(flow)) } }
        }
        block()
    }

    @Test
    fun `a body that is not form-posted is refused unparsed`() = backchannelTest {
        val malformed = client.post("/auth/oidc/logout/backchannel") {
            header(HttpHeaders.ContentType, "multipart/form-data")
        }
        assertEquals(HttpStatusCode.UnsupportedMediaType, malformed.status)

        val json = client.post("/auth/oidc/logout/backchannel") {
            contentType(ContentType.Application.Json)
            setBody("""{"logout_token": "x"}""")
        }
        assertEquals(HttpStatusCode.UnsupportedMediaType, json.status)
    }

    @Test
    fun `a form post without a logout token is a bad request`() = backchannelTest {
        val response = client.submitForm("/auth/oidc/logout/backchannel", parameters { append("other", "x") })
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
