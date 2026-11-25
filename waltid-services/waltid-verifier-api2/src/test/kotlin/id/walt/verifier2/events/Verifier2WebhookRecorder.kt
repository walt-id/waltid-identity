package id.walt.verifier2.events

import com.sun.net.httpserver.HttpServer
import id.walt.commons.config.NetworkFetchGuardConfig
import id.walt.commons.config.apply
import id.walt.ktornotifications.WEBHOOK_SEQUENCE_HEADER
import id.walt.ktornotifications.core.KtorSessionNotifications
import id.walt.ktornotifications.core.KtorSessionUpdate
import id.walt.verifier2.data.SessionEvent
import id.walt.webdatafetching.ssrf.PrivateNetworkGuardSettings
import io.ktor.http.Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Captures verifier2 webhook payloads so tests can assert [SessionEvent] emission independently
 * of SSE subscribers.
 */
class Verifier2WebhookRecorder : AutoCloseable {
    private val receivedUpdates = ConcurrentLinkedQueue<ReceivedCallback>()
    private var server: HttpServer? = null
    private var previousGuard: GuardSnapshot? = null

    fun start(allowLoopback: Boolean = true): Verifier2WebhookRecorder {
        previousGuard = GuardSnapshot.capture()
        val previous = previousGuard!!
        NetworkFetchGuardConfig(
            allowLoopback = allowLoopback,
            allowPrivateNetworks = previous.allowPrivateNetworks,
            trustedHostSuffixes = previous.trustedHostSuffixes.toList(),
        ).apply()
        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        httpServer.createContext("/webhook") { exchange ->
            val sequence = exchange.requestHeaders.getFirst(WEBHOOK_SEQUENCE_HEADER)?.toLongOrNull()
                ?: error("Webhook callback is missing $WEBHOOK_SEQUENCE_HEADER")
            val body = exchange.requestBody.readBytes().decodeToString()
            receivedUpdates += ReceivedCallback(json.decodeFromString(body), sequence)
            val response = "ok".toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        httpServer.start()
        server = httpServer
        return this
    }

    fun url(): Url {
        val address = server?.address ?: error("Webhook recorder was not started")
        return Url("http://127.0.0.1:${address.port}/webhook")
    }

    fun notifications(): KtorSessionNotifications =
        KtorSessionNotifications(
            webhook = KtorSessionNotifications.VerificationSessionWebhookNotification(url = url())
        )

    fun received(): List<KtorSessionUpdate> = receivedInEnqueueOrder()

    fun awaitPresentationEvents(
        sessionId: String,
        presentationId: String,
        expected: List<SessionEvent>,
        timeoutMillis: Long = 8_000,
    ): List<KtorSessionUpdate> {
        val expectedNames = expected.map { it.name }
        val deadline = System.currentTimeMillis() + timeoutMillis
        var updates = emptyList<KtorSessionUpdate>()
        while (System.currentTimeMillis() < deadline) {
            updates = receivedUpdates.filter {
                it.update.target == sessionId &&
                    it.update.session["presentationId"]?.jsonPrimitive?.content == presentationId &&
                    it.update.event != SessionEvent.authorization_request_requested.name
            }.sortedBy { it.sequence }.map { it.update }
            if (updates.map { it.event } == expectedNames) return updates
            if (updates.size > expectedNames.size) break
            Thread.sleep(50)
        }
        throw AssertionError(
            "Presentation '$presentationId' did not emit callback events $expectedNames. " +
                "Received: ${updates.map { it.event }}"
        )
    }

    fun awaitEvents(sessionId: String, timeoutMillis: Long = 5_000): List<KtorSessionUpdate> {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val events = receivedUpdates.filter { it.update.target == sessionId }
            if (events.isNotEmpty()) return events.sortedBy { it.sequence }.map { it.update }
            Thread.sleep(50)
        }
        throw AssertionError(
            "Timed out waiting for webhook events for session '$sessionId'. Received: " +
                receivedUpdates.joinToString { "${it.update.target}:${it.update.event}" }
        )
    }

    fun assertReceivedInOrder(sessionId: String, expected: List<SessionEvent>, timeoutMillis: Long = 8_000) {
        val expectedNames = expected.map { it.name }
        val deadline = System.currentTimeMillis() + timeoutMillis
        var events = emptyList<String>()
        while (System.currentTimeMillis() < deadline) {
            events = eventsInEnqueueOrder(sessionId)
            if (events.size > expectedNames.size) break
            if (events.size == expectedNames.size && events == expectedNames) return
            Thread.sleep(50)
        }
        throw AssertionError(
            "Session '$sessionId' did not emit exact callback events $expectedNames in Webhook-Sequence order. " +
                "Received: $events"
        )
    }

    fun assertDoesNotContain(sessionId: String, unexpected: SessionEvent) {
        val events = eventsInEnqueueOrder(sessionId)
        assertTrue(
            unexpected.name !in events,
            "Session '$sessionId' unexpectedly emitted '${unexpected.name}'. Received: $events"
        )
    }

    fun assertSessionStatus(sessionId: String, event: SessionEvent, expectedStatus: String) {
        val update = receivedUpdates.firstOrNull { it.update.target == sessionId && it.update.event == event.name }
            ?: error("Missing event '${event.name}' for session '$sessionId'. Received: ${receivedUpdates.map { it.update.event }}")
        val status = update.update.session["status"]?.toString()?.trim('"')
        assertEquals(expectedStatus, status, "Unexpected status on '${event.name}'")
    }

    override fun close() {
        server?.stop(0)
        server = null
        receivedUpdates.clear()
        previousGuard?.restore()
        previousGuard = null
    }

    private fun eventsInEnqueueOrder(sessionId: String): List<String> =
        receivedUpdates.filter { it.update.target == sessionId }
            .sortedBy { it.sequence }
            .map { it.update.event }

    private fun receivedInEnqueueOrder(): List<KtorSessionUpdate> =
        receivedUpdates.sortedBy { it.sequence }.map { it.update }

    private data class ReceivedCallback(
        val update: KtorSessionUpdate,
        val sequence: Long,
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

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        val successfulPresentationEvents = listOf(
            SessionEvent.authorization_request_requested,
            SessionEvent.attempted_presentation,
            SessionEvent.parsed_presentation_available,
            SessionEvent.presentation_validation_available,
            SessionEvent.validated_credentials_available,
            SessionEvent.presentation_fulfils_dcql_query,
            SessionEvent.credential_policy_results_available,
        )

        val presentationValidationFailureEvents = listOf(
            SessionEvent.authorization_request_requested,
            SessionEvent.attempted_presentation,
            SessionEvent.parsed_presentation_available,
            SessionEvent.presentation_validation_available,
            SessionEvent.presentation_validation_failed,
        )

        val walletRejectionEvents = listOf(
            SessionEvent.authorization_request_requested,
            SessionEvent.wallet_error_response_received,
        )

    }
}
