package id.walt.verifier2.events

import com.sun.net.httpserver.HttpServer
import id.walt.ktornotifications.core.KtorSessionNotifications
import id.walt.ktornotifications.core.KtorSessionUpdate
import id.walt.verifier2.data.SessionEvent
import io.ktor.http.Url
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Captures verifier2 webhook payloads so tests can assert [SessionEvent] emission independently
 * of SSE subscribers.
 */
class Verifier2WebhookRecorder : AutoCloseable {
    private val receivedUpdates = ConcurrentLinkedQueue<KtorSessionUpdate>()
    private var server: HttpServer? = null

    fun start(): Verifier2WebhookRecorder {
        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        httpServer.createContext("/webhook") { exchange ->
            val body = exchange.requestBody.readBytes().decodeToString()
            receivedUpdates += json.decodeFromString<KtorSessionUpdate>(body)
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

    fun received(): List<KtorSessionUpdate> = receivedUpdates.toList()

    fun awaitEvents(sessionId: String, timeoutMillis: Long = 5_000): List<KtorSessionUpdate> {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val events = receivedUpdates.filter { it.target == sessionId }
            if (events.isNotEmpty()) return events
            Thread.sleep(50)
        }
        throw AssertionError(
            "Timed out waiting for webhook events for session '$sessionId'. Received: " +
                receivedUpdates.joinToString { "${it.target}:${it.event}" }
        )
    }

    fun assertReceivedInOrder(sessionId: String, expected: List<SessionEvent>, timeoutMillis: Long = 8_000) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var events = emptyList<KtorSessionUpdate>()
        while (System.currentTimeMillis() < deadline) {
            events = receivedUpdates.filter { it.target == sessionId }
            if (containsInOrder(events.map { it.event }, expected.map { it.name })) {
                return
            }
            Thread.sleep(50)
        }
        throw AssertionError(
            "Session '$sessionId' did not emit expected callback events $expected. Received: " +
                events.map { it.event }
        )
    }

    fun assertDoesNotContain(sessionId: String, unexpected: SessionEvent) {
        val events = receivedUpdates.filter { it.target == sessionId }.map { it.event }
        assertTrue(
            unexpected.name !in events,
            "Session '$sessionId' unexpectedly emitted '${unexpected.name}'. Received: $events"
        )
    }

    fun assertSessionStatus(sessionId: String, event: SessionEvent, expectedStatus: String) {
        val update = receivedUpdates.firstOrNull { it.target == sessionId && it.event == event.name }
            ?: error("Missing event '${event.name}' for session '$sessionId'. Received: ${receivedUpdates.map { it.event }}")
        val status = update.session["status"]?.toString()?.trim('"')
        assertEquals(expectedStatus, status, "Unexpected status on '${event.name}'")
    }

    override fun close() {
        server?.stop(0)
        server = null
        receivedUpdates.clear()
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

        private fun containsInOrder(actual: List<String>, expected: List<String>): Boolean {
            var index = 0
            for (event in actual) {
                if (index < expected.size && event == expected[index]) {
                    index++
                }
            }
            return index == expected.size
        }
    }
}
