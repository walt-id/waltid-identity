package id.walt.policies2.vc.status.status

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import kotlinx.serialization.json.Json

class StatusCredentialTestServer {

    private lateinit var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>
    private lateinit var baseUrl: String

    lateinit var credentials: Map<String, List<TestStatusResource>>
        private set

    val port: Int
        get() = _port ?: throw IllegalStateException("Server not started yet")

    private var _port: Int? = null
    private var started = false

    suspend fun start() {
        if (!started) {
            server = embeddedServer(Netty, port = 0, module = { module() }).start(wait = false)

            _port = server.engine.resolvedConnectors().first().port
            baseUrl = "http://localhost:$port"

            credentials = resourceReader.readResourcesBySubfolder(
                "status",
                placeholderValue = "$baseUrl/$STATUS_CREDENTIAL_PATH"
            )

            started = true
        }
    }

    fun stop() {
        if (started) {
            server.stop()
            started = false
        }
    }

    companion object {
        private const val STATUS_CREDENTIAL_PATH = "credentials"
        private val resourceReader = StatusTestUtils.TestResourceReader()
    }

    private fun Application.module() {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        routing {
            get("credentials/{id}") {
                val id = call.parameters.getOrFail("id")
                val statusCredential = getStatusCredentialContent(credentials.values.flatten(), id)
                requireNotNull(statusCredential)
                call.respond<String>(statusCredential)
            }
        }
    }

    private fun getStatusCredentialContent(resources: List<TestStatusResource>, targetId: String): String? =
        // match TestStatusResource id
        resources.find { it.id == targetId }?.let { testResource ->
            when (val data = testResource.data) {
                is MultiStatusResourceData -> data.statusCredential.firstOrNull()?.content
                is SingleStatusResourceData -> data.statusCredential
            }
        } ?: // match MultiStatusResourceData id
        resources
            .mapNotNull { it.data as? MultiStatusResourceData }
            .flatMap { it.statusCredential }
            .find { it.id == targetId }
            ?.content
}
