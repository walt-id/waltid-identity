package id.walt.policies.policies.status.status

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.util.getOrFail
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
                call.respond<String>(credentials.values.flatten().find { it.id == id }!!.data.statusCredential)
            }
        }
    }
}