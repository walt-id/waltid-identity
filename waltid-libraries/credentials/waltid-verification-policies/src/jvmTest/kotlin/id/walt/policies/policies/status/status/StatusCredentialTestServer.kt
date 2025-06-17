package id.walt.policies.policies.status.status

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import kotlinx.serialization.json.Json

object StatusCredentialTestServer {
    private const val PORT = 8080
    private const val URL = "http://localhost:$PORT"
    private const val STATUS_CREDENTIAL_PATH = "credentials"
    private var serverStarted = false

    private val resourceReader = StatusTestUtils.TestResourceReader()
    val credentials = resourceReader.readResourcesBySubfolder(
        "status",
        placeholderValue = "$URL/$STATUS_CREDENTIAL_PATH",
    )

    private val server by lazy {
        println("Initializing embedded webserver...")
        embeddedServer(Netty, configure = {
            connector {
                port = 8080
            }
        }, module = { module() })
    }

    fun start() {
        if (!serverStarted) {
            println("Starting status credential test server...")
            server.start()
            serverStarted = true
        }
    }

    fun stop() {
        if (serverStarted) {
            println("Stopping status credential test server...")
            server.stop()
        }
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