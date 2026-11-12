package id.walt.issuer2.web.plugins

import id.walt.commons.config.ConfigManager
import id.walt.commons.web.modules.AuthenticationServiceModule
import id.walt.issuer2.config.AuthenticationServiceConfig
import id.walt.issuer2.config.Issuer2ServiceConfig
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Issuer2OAuthSecretTest {
    @AfterEach
    fun clearConfig() {
        ConfigManager.preclear()
    }

    @Test
    fun `external OAuth reports missing or blank client secrets when provider is looked up`() {
        for (secret in listOf("", "   \t")) {
            testApplication {
                installOAuthRoute(secret)
                val response = client.get("/external-login")

                assertEquals(HttpStatusCode.InternalServerError, response.status)
                assertEquals(
                    "Issuer OAuth client secret is missing or blank; configure KEYCLOAK_CLIENT_SECRET or an inline clientSecret",
                    response.bodyAsText(),
                )
            }
            ConfigManager.preclear()
        }
    }

    @Test
    fun `external OAuth accepts a configured client secret`() = testApplication {
        installOAuthRoute("synthetic-inline-secret")
        val response = createClient { followRedirects = false }.get("/external-login")

        assertEquals(HttpStatusCode.Found, response.status)
        assertTrue(response.headers["Location"].orEmpty().startsWith("https://keycloak.demo.walt.id/"))
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.installOAuthRoute(secret: String) {
        application {
            ConfigManager.preloadAndRegisterConfig(
                "authentication-service",
                AuthenticationServiceConfig(clientSecret = secret),
            )
            ConfigManager.preloadAndRegisterConfig("issuer-service", Issuer2ServiceConfig(baseUrl = "http://localhost"))
            ConfigManager.loadConfigs()
            issuer2AuthenticationPluginAmendment()
            AuthenticationServiceModule.run { enable() }
            install(StatusPages) {
                exception<IllegalStateException> { call, cause ->
                    call.respondText(cause.message.orEmpty(), status = HttpStatusCode.InternalServerError)
                }
            }
            routing {
                authenticate("auth-oauth") {
                    get("/external-login") { }
                }
            }
        }
    }
}
