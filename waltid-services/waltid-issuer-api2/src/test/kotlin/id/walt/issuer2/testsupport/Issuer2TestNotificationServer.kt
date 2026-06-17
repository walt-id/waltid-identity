package id.walt.issuer2.testsupport

import id.walt.issuer2.notifications.IssuanceSessionEvent
import id.walt.ktornotifications.core.KtorSessionUpdate
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentLinkedQueue

class Issuer2TestNotificationServer {
    private val receivedUpdates = ConcurrentLinkedQueue<KtorSessionUpdate>()
    private var testServer: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    private fun Application.module() {
        install(ContentNegotiation) {
            json(issuer2TestJson)
        }
        routing {
            post("/webhook/issuer2") {
                call.receiveUpdate()
            }
            post("/webhook/issuer2/{sessionId}") {
                call.receiveUpdate()
            }
        }
    }

    fun startServer() {
        testServer = embeddedServer(
            CIO,
            host = "127.0.0.1",
            port = 0,
            module = { module() },
        ).start(wait = false)
    }

    fun stopServer() {
        testServer?.stop(500, 500)
        testServer = null
    }

    fun reset() {
        receivedUpdates.clear()
    }

    fun webhookUrl(): String = "${getBaseUrl()}/webhook/issuer2"

    fun getBaseUrl(): String = runBlocking {
        val connector = requireNotNull(testServer) { "Notification server is not started" }
            .engine
            .resolvedConnectors()
            .first()
        "http://127.0.0.1:${connector.port}"
    }

    fun getReceivedUpdates(): List<KtorSessionUpdate> = receivedUpdates.toList()

    fun awaitEvent(
        sessionId: String,
        event: IssuanceSessionEvent,
        timeoutMillis: Long = 5_000,
    ): KtorSessionUpdate {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            receivedUpdates.firstOrNull { it.target == sessionId && it.event == event.toString() }?.let { return it }
            Thread.sleep(50)
        }
        throw AssertionError(
            "Timed out waiting for event '$event' for session '$sessionId'. Received events: " +
                receivedUpdates.joinToString { "${it.target}:${it.event}" },
        )
    }

    private suspend fun ApplicationCall.receiveUpdate() {
        receivedUpdates += receive<KtorSessionUpdate>()
        respond(HttpStatusCode.OK)
    }
}
