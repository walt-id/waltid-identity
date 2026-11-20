package id.walt.issuer2.testsupport

import id.walt.commons.config.ConfigManager
import id.walt.issuer2.config.AuthenticationServiceConfig
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.issuer2Module
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.Url
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.application.install

class Issuer2BrowserTestServer(
    val baseUrl: String = System.getenv("ISSUER2_BROWSER_TEST_BASE_URL")?.takeIf(String::isNotBlank) ?: DEFAULT_REAL_SERVER_BASE_URL,
    val requirePar: Boolean = false,
) : AutoCloseable {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    fun start(): Issuer2BrowserTestServer {
        loadIssuer2ConfigFiles(baseUrlOverride = baseUrl)
        val config = ConfigManager.getConfig<Issuer2ServiceConfig>()
        ConfigManager.loadedConfigurations["issuer-service" to Issuer2ServiceConfig::class] =
            config.copy(enforcePushedAuthorizationRequests = requirePar)
        System.getenv("KEYCLOAK_URL")?.takeIf(String::isNotBlank)?.trimEnd('/')?.let { realm ->
            val auth = ConfigManager.getConfig<AuthenticationServiceConfig>()
            ConfigManager.loadedConfigurations["authentication-service" to AuthenticationServiceConfig::class] = auth.copy(
                authorizeUrl = "$realm/protocol/openid-connect/auth",
                accessTokenUrl = "$realm/protocol/openid-connect/token",
            )
        }
        val url = Url(baseUrl)
        server = embeddedServer(
            factory = CIO,
            host = "0.0.0.0",
            port = url.port,
        ) {
            install(ServerContentNegotiation) {
                json(issuer2TestJson)
            }
            installIssuer2AuthenticationForTests()
            issuer2Module(withPlugins = true)
        }.start(wait = false)
        return this
    }

    fun httpClient(): HttpClient =
        HttpClient(Java) {
            followRedirects = false
            install(ClientContentNegotiation) {
                json(issuer2TestJson)
            }
            defaultRequest {
                url(baseUrl)
            }
        }

    override fun close() {
        server?.stop(500, 500)
        server = null
        clearIssuer2TestEnvironment()
    }

    companion object {
        const val DEFAULT_REAL_SERVER_BASE_URL = "http://localhost:7002"
    }
}
