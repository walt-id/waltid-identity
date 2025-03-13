package id.walt.webwallet.service.credentials.status.fetch

import TestUtils
import id.walt.webwallet.service.JwsDecoder
import id.walt.webwallet.service.credentials.status.fetch.DefaultStatusListCredentialFetchStrategyTest.StatusCredentialTestServer.credentialResourcePath
import id.walt.webwallet.service.credentials.status.fetch.DefaultStatusListCredentialFetchStrategyTest.StatusCredentialTestServer.serverStarted
import io.ktor.client.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DefaultStatusListCredentialFetchStrategyTest {

    private val sut = DefaultStatusListCredentialFetchStrategy(HttpClient(), JwsDecoder())

    @BeforeAll
    fun startServer() {
        if (!serverStarted) {
            println("Starting status credential test server...")
            StatusCredentialTestServer.server.start()
            serverStarted = true
        }
    }

    @AfterAll
    fun stopServer() {
        if (serverStarted) {
            StatusCredentialTestServer.server.stop()
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "revocation-list-with-status-message",
            "revocation-list-with-status-message-vc-wrapped",
            "revocation-list-with-status-message-jwt",
        ]
    )
    fun test(id: String) = runTest {
        // given
        val resource = TestUtils.loadResource("$credentialResourcePath/revocation-list-with-status-message.json")
        val credential = Json.decodeFromString<JsonObject>(resource)
        val url = getUrl(id)
        // when
        val result = sut.fetch(url)
        // then
        assertEquals(expected = credential, actual = result)
    }

    private fun getUrl(id: String) = "http://localhost:${StatusCredentialTestServer.serverPort}/${
        String.format(
            StatusCredentialTestServer.statusCredentialPath,
            id
        )
    }"

    object StatusCredentialTestServer {
        var serverStarted = false
        const val serverPort = 8085
        const val credentialResourcePath = "credential-status/status-list-credential"
        const val statusCredentialPath = "credentials/%s"

        val server: EmbeddedServer<*, *> by lazy {
            println("Initializing embedded webserver...")
            embeddedServer(CIO, applicationEnvironment(), { envConfig() }, module = { module() })
        }

        private fun Application.module() {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            routing {
                get("credentials/{id}") {
                    val id = call.parameters.getOrFail("id")
                    val credential = TestUtils.loadResource("$credentialResourcePath/$id.json")
                    call.respond<String>(credential)
                }
            }
        }

        private fun ApplicationEngine.Configuration.envConfig() {
            connector {
                port = serverPort
            }
        }
    }
}
