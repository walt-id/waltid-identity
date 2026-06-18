package id.walt.issuer2.testsupport

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
    val baseUrl: String = DEFAULT_REAL_SERVER_BASE_URL,
) : AutoCloseable {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    fun start(): Issuer2BrowserTestServer {
        loadIssuer2ConfigFiles(baseUrlOverride = baseUrl)
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
