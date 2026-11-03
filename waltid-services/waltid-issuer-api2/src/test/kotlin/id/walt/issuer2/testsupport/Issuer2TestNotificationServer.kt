package id.walt.issuer2.testsupport

import id.walt.commons.config.NetworkFetchGuardConfig
import id.walt.commons.config.apply
import id.walt.issuer2.notifications.IssuanceSessionEvent
import id.walt.ktornotifications.WEBHOOK_SEQUENCE_HEADER
import id.walt.ktornotifications.WEBHOOK_ID_HEADER
import id.walt.ktornotifications.IDEMPOTENCY_KEY_HEADER
import id.walt.ktornotifications.WEBHOOK_TIMESTAMP_HEADER
import id.walt.ktornotifications.WEBHOOK_ATTEMPT_HEADER
import id.walt.ktornotifications.CONTENT_DIGEST_HEADER
import id.walt.ktornotifications.WEBHOOK_SIGNATURE_HEADER
import id.walt.ktornotifications.core.KtorSessionUpdate
import id.walt.webdatafetching.ssrf.PrivateNetworkGuardSettings
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
import io.ktor.server.request.receiveText
import io.ktor.server.request.request
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentLinkedQueue

class Issuer2TestNotificationServer {
    private val receivedUpdates = ConcurrentLinkedQueue<ReceivedCallback>()
    private var testServer: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var previousGuard: GuardSnapshot? = null

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
        previousGuard = GuardSnapshot.capture()
        val previous = previousGuard!!
        NetworkFetchGuardConfig(
            allowLoopback = true,
            allowPrivateNetworks = previous.allowPrivateNetworks,
            trustedHostSuffixes = previous.trustedHostSuffixes.toList(),
        ).apply()
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
        previousGuard?.restore()
        previousGuard = null
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

    fun getReceivedUpdates(): List<KtorSessionUpdate> =
        receivedUpdates.sortedBy { it.sequence }.map { it.update }

    fun getReceivedCallbacks(): List<ReceivedCallback> = receivedUpdates.sortedBy { it.sequence }

    fun orderedEvents(target: String): List<String> =
        receivedUpdates.filter { it.update.target == target }
            .sortedBy { it.sequence }
            .map { it.update.event }

    fun awaitEvent(
        sessionId: String,
        event: IssuanceSessionEvent,
        timeoutMillis: Long = 5_000,
    ): KtorSessionUpdate {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            receivedUpdates.firstOrNull { it.update.target == sessionId && it.update.event == event.value }
                ?.let { return it.update }
            Thread.sleep(50)
        }
        throw AssertionError(
            "Timed out waiting for event '$event' for session '$sessionId'. Received events: " +
                receivedUpdates.joinToString { "${it.update.target}:${it.update.event}" },
        )
    }

    private suspend fun ApplicationCall.receiveUpdate() {
        val sequence = request.headers[WEBHOOK_SEQUENCE_HEADER]?.toLongOrNull()
            ?: throw IllegalArgumentException("Webhook callback is missing $WEBHOOK_SEQUENCE_HEADER")
        val body = receiveText()
        receivedUpdates += ReceivedCallback(
            update = issuer2TestJson.decodeFromString<KtorSessionUpdate>(body),
            sequence = sequence,
            rawBody = body,
            deliveryId = request.headers[WEBHOOK_ID_HEADER],
            idempotencyKey = request.headers[IDEMPOTENCY_KEY_HEADER],
            timestamp = request.headers[WEBHOOK_TIMESTAMP_HEADER],
            attempt = request.headers[WEBHOOK_ATTEMPT_HEADER],
            contentDigest = request.headers[CONTENT_DIGEST_HEADER],
            signature = request.headers[WEBHOOK_SIGNATURE_HEADER],
        )
        respond(HttpStatusCode.OK)
    }

    data class ReceivedCallback(
        val update: KtorSessionUpdate,
        val sequence: Long,
        val rawBody: String,
        val deliveryId: String?,
        val idempotencyKey: String?,
        val timestamp: String?,
        val attempt: String?,
        val contentDigest: String?,
        val signature: String?,
    )

    private data class GuardSnapshot(
        val allowLoopback: Boolean,
        val allowLoopbackExplicit: Boolean,
        val allowPrivateNetworks: Boolean,
        val trustedHostSuffixes: Set<String>,
    ) {
        fun restore() {
            if (allowLoopbackExplicit) {
                PrivateNetworkGuardSettings.allowLoopback = allowLoopback
            } else {
                PrivateNetworkGuardSettings.clearAllowLoopbackChoice()
            }
            PrivateNetworkGuardSettings.allowPrivateNetworks = allowPrivateNetworks
            PrivateNetworkGuardSettings.trustedHostSuffixes = trustedHostSuffixes
        }

        companion object {
            fun capture() = GuardSnapshot(
                allowLoopback = PrivateNetworkGuardSettings.allowLoopback,
                allowLoopbackExplicit = PrivateNetworkGuardSettings.allowLoopbackExplicit,
                allowPrivateNetworks = PrivateNetworkGuardSettings.allowPrivateNetworks,
                trustedHostSuffixes = PrivateNetworkGuardSettings.trustedHostSuffixes,
            )
        }
    }
}
