package id.walt.ktornotifications

import id.walt.ktornotifications.core.KtorSessionNotifications
import id.walt.ktornotifications.core.KtorSessionUpdate
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64
import kotlin.io.path.deleteIfExists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebhookDeliveryQueueTest {
    private val temporaryDirectories = mutableListOf<Path>()

    @AfterTest
    fun cleanUp() {
        temporaryDirectories.forEach { directory ->
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Path::deleteIfExists)
            }
        }
    }

    @Test
    fun `retry keeps delivery id body and digest stable across process restart`() = runTest {
        val root = temporaryDirectory()
        var now = 1_000_000L
        val firstTransport = RecordingTransport(WebhookAttemptResult(statusCode = 503))
        val firstStore = FileWebhookDeliveryStore(root)
        val firstQueue = queue(firstStore, firstTransport) { now }

        val deliveryId = firstQueue.enqueue(update(), webhook(maxAttempts = 3))
        firstQueue.processDue()

        val persisted = assertNotNull(firstStore.pending(deliveryId))
        assertEquals(1, persisted.attemptCount)
        assertEquals(deliveryId, firstTransport.deliveries.single().deliveryId)

        // A new store/queue represents a restarted service reading the durable outbox.
        now = persisted.nextAttemptAtEpochMilliseconds
        val secondTransport = RecordingTransport(WebhookAttemptResult(statusCode = 204))
        val restartedStore = FileWebhookDeliveryStore(root)
        val restartedQueue = queue(restartedStore, secondTransport) { now }
        restartedQueue.processDue()

        val firstAttempt = firstTransport.deliveries.single()
        val secondAttempt = secondTransport.deliveries.single()
        assertEquals(firstAttempt.deliveryId, secondAttempt.deliveryId)
        assertEquals(firstAttempt.payload, secondAttempt.payload)
        assertEquals(firstAttempt.contentDigest, secondAttempt.contentDigest)
        assertEquals(expectedDigest(firstAttempt.payload), firstAttempt.contentDigest)
        assertNull(restartedStore.pending(deliveryId))
    }

    @Test
    fun `distinct events receive distinct delivery ids`() = runTest {
        val queue = queue(FileWebhookDeliveryStore(temporaryDirectory()), RecordingTransport()) { 1_000L }

        val first = queue.enqueue(update(event = "first"), webhook())
        val second = queue.enqueue(update(event = "second"), webhook())

        assertNotEquals(first, second)
    }

    @Test
    fun `events enqueued in the same millisecond are delivered in order`() = runTest {
        val transport = RecordingTransport()
        val queue = queue(FileWebhookDeliveryStore(temporaryDirectory()), transport) { 1_000L }

        queue.enqueue(update(event = "first"), webhook())
        queue.enqueue(update(event = "second"), webhook())
        queue.processDue()

        assertEquals(listOf("first", "second"), transport.deliveries.map(WebhookDelivery::event))
    }

    @Test
    fun `permanent rejection is dead-lettered and can be redriven`() = runTest {
        val store = FileWebhookDeliveryStore(temporaryDirectory())
        val transport = RecordingTransport(
            WebhookAttemptResult(statusCode = 400),
            WebhookAttemptResult(statusCode = 200),
        )
        val queue = queue(store, transport) { 2_000L }
        val deliveryId = queue.enqueue(update(), webhook())

        queue.processDue()
        val deadLetter = queue.deadLetters().single()
        assertEquals(deliveryId, deadLetter.deliveryId)
        assertEquals(1, deadLetter.attemptCount)
        assertTrue(deadLetter.lastError.contains("HTTP 400"))
        assertNull(store.pending(deliveryId))

        assertTrue(queue.redrive(deliveryId))
        queue.processDue()
        assertTrue(queue.deadLetters().isEmpty())
        assertNull(store.pending(deliveryId))
        assertEquals(listOf(1, 1), transport.attempts)
    }

    @Test
    fun `retryable response is dead-lettered after max attempts`() = runTest {
        val store = FileWebhookDeliveryStore(temporaryDirectory())
        val transport = RecordingTransport(
            WebhookAttemptResult(statusCode = 503),
            WebhookAttemptResult(statusCode = 503),
        )
        val queue = queue(store, transport) { 3_000L }
        val deliveryId = queue.enqueue(update(), webhook(maxAttempts = 2))

        queue.processDue()
        queue.processDue()

        val deadLetter = queue.deadLetters().single()
        assertEquals(deliveryId, deadLetter.deliveryId)
        assertEquals(2, deadLetter.attemptCount)
        assertEquals(listOf(1, 2), transport.attempts)
    }

    @Test
    fun `transport sends integrity idempotency and attempt headers`() = runTest {
        val payload = """{"event":"completed"}"""
        val delivery = delivery(payload)
        var requestObserved = false
        val client = HttpClient(MockEngine { request ->
            requestObserved = true
            assertEquals(delivery.deliveryId, request.headers[WEBHOOK_ID_HEADER])
            assertEquals(delivery.deliveryId, request.headers[IDEMPOTENCY_KEY_HEADER])
            assertEquals("123", request.headers[WEBHOOK_TIMESTAMP_HEADER])
            assertEquals("2", request.headers[WEBHOOK_ATTEMPT_HEADER])
            assertEquals(delivery.contentDigest, request.headers[CONTENT_DIGEST_HEADER])
            assertEquals(payload, (request.body as TextContent).text)
            assertEquals("application/json", request.body.contentType?.withoutParameters().toString())
            respond(
                content = "",
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(HttpHeaders.RetryAfter, "7"),
            )
        })

        val result = KtorWebhookTransport(client) { 123_000L }.deliver(delivery, 2, 123)

        assertTrue(requestObserved)
        assertEquals(429, result.statusCode)
        assertEquals(7_000L, result.retryAfterMilliseconds)
        assertTrue(result.retryable)
        assertFalse(result.successful)
        client.close()
    }

    private fun temporaryDirectory(): Path =
        Files.createTempDirectory("webhook-delivery-test-").also(temporaryDirectories::add)

    private fun queue(
        store: WebhookDeliveryStore,
        transport: WebhookTransport,
        clock: () -> Long,
    ) = WebhookDeliveryQueue(
        store = store,
        transport = transport,
        clock = clock,
        jitter = { 0 },
    )

    private fun update(event: String = "completed") = KtorSessionUpdate(
        target = "session-1",
        event = event,
        session = buildJsonObject { put("status", "done") },
        requestId = "request-1",
    )

    private fun webhook(maxAttempts: Int = 5) =
        KtorSessionNotifications.VerificationSessionWebhookNotification(
            url = io.ktor.http.Url("https://receiver.example/webhook"),
            retryPolicy = KtorSessionNotifications.WebhookRetryPolicy(
                maxAttempts = maxAttempts,
                initialBackoffSeconds = 0,
                maxBackoffSeconds = 0,
            ),
        )

    private fun delivery(payload: String) = WebhookDelivery(
        deliveryId = "delivery-1",
        url = "https://receiver.example/webhook",
        payload = payload,
        contentDigest = expectedDigest(payload),
        target = "session-1",
        event = "completed",
        requestId = "request-1",
        basicAuthUser = null,
        basicAuthPass = null,
        bearerToken = null,
        retryPolicy = KtorSessionNotifications.WebhookRetryPolicy(),
        enqueueOrder = 123_000_000L,
        createdAtEpochMilliseconds = 123_000L,
        nextAttemptAtEpochMilliseconds = 123_000L,
    )

    private fun expectedDigest(payload: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray())
        return "sha-256=:${Base64.getEncoder().encodeToString(digest)}:"
    }

    private class RecordingTransport(vararg results: WebhookAttemptResult) : WebhookTransport {
        private val results = ArrayDeque(results.toList())
        val deliveries = mutableListOf<WebhookDelivery>()
        val attempts = mutableListOf<Int>()

        override suspend fun deliver(
            delivery: WebhookDelivery,
            attempt: Int,
            timestampEpochSeconds: Long,
        ): WebhookAttemptResult {
            deliveries += delivery
            attempts += attempt
            return if (results.isEmpty()) WebhookAttemptResult(statusCode = 200) else results.removeFirst()
        }
    }
}
