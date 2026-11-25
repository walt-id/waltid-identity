package id.walt.ktornotifications

import id.walt.ktornotifications.core.KtorSessionNotifications
import id.walt.ktornotifications.core.KtorSessionUpdate
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.deleteIfExists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebhookDeliveryQueueTest {
    private val temporaryDirectories = mutableListOf<Path>()

    @Test
    fun `signing secret requires at least 32 UTF-8 bytes`() {
        val config = webhook()
        assertFailsWith<IllegalArgumentException> { config.copy(signingSecret = "") }
        assertFailsWith<IllegalArgumentException> { config.copy(signingSecret = " ".repeat(32)) }
        assertFailsWith<IllegalArgumentException> { config.copy(signingSecret = "x".repeat(31)) }
        assertEquals("x".repeat(32), config.copy(signingSecret = "x".repeat(32)).signingSecret)
        assertEquals("é".repeat(16), config.copy(signingSecret = "é".repeat(16)).signingSecret)
    }

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
    fun `corrupt pending record is quarantined without blocking healthy deliveries`() = runTest {
        val root = temporaryDirectory()
        val store = FileWebhookDeliveryStore(root)
        val transport = RecordingTransport()
        val queue = queue(store, transport) { 1_000L }
        val healthyDeliveryId = queue.enqueue(update(), webhook())
        val corruptPath = root.resolve("pending/corrupt.json")
        Files.writeString(corruptPath, "{truncated")
        Files.write(root.resolve("pending/invalid-encoding.json"), byteArrayOf(0xff.toByte()))

        queue.processDue()

        assertNull(store.pending(healthyDeliveryId))
        assertEquals(1, transport.deliveries.size)
        assertFalse(Files.exists(corruptPath))
        assertEquals(
            2L,
            Files.list(root.resolve("corrupt")).use { files -> files.count() },
        )
    }

    @Test
    fun `an interrupted file transition keeps the dead letter until redrive commits`() = runTest {
        val root = temporaryDirectory()
        val store = FileWebhookDeliveryStore(root)
        val delivery = delivery("{}")
        store.enqueue(delivery)
        val now = delivery.createdAtEpochMilliseconds
        val claim = store.claimDue(now, now + 1).single()
        val pending = root.resolve("pending/${delivery.deliveryId}.json")
        val leasedRecord = Files.readAllBytes(pending)
        val failed = claim.delivery.copy(failedAtEpochMilliseconds = now, lastError = "HTTP 400")
        assertTrue(store.moveToDeadLetter(claim, failed))
        val dead = root.resolve("dead-letter/${delivery.deliveryId}.json")
        val deadRecord = Files.readAllBytes(dead)

        // Process exited after publishing failure but before removing the leased pending file.
        Files.write(pending, leasedRecord)
        val restarted = FileWebhookDeliveryStore(root)
        assertTrue(restarted.claimDue(now + 2, now + 3).isEmpty())
        assertFalse(restarted.markDelivered(claim))
        assertEquals(delivery.deliveryId, restarted.deadLetters().single().deliveryId)

        // Process exited after writing a redriven record but before removing the dead letter.
        assertTrue(restarted.redrive(delivery.deliveryId, now + 2))
        Files.write(dead, deadRecord)
        assertNull(FileWebhookDeliveryStore(root).pending(delivery.deliveryId))
        assertTrue(restarted.redrive(delivery.deliveryId, now + 2))
        assertEquals(delivery.deliveryId, restarted.claimDue(now + 2, now + 3).single().delivery.deliveryId)
        assertTrue(restarted.deadLetters().isEmpty())
    }

    @Test
    fun `crash after lease does not consume an attempt`() = runTest {
        val root = temporaryDirectory()
        val durableStore = FileWebhookDeliveryStore(root)
        val crashAfterLeaseStore = CrashAfterLeaseStore(durableStore)
        val firstTransport = RecordingTransport()
        val firstQueue = queue(crashAfterLeaseStore, firstTransport) { 1_000L }
        val deliveryId = firstQueue.enqueue(update(), webhook(maxAttempts = 1))

        assertFailsWith<SimulatedCrashException> {
            firstQueue.processDue()
        }

        val leased = assertNotNull(durableStore.pending(deliveryId))
        assertEquals(0, leased.attemptCount)
        assertTrue(firstTransport.deliveries.isEmpty())

        val restartedTransport = RecordingTransport()
        val restartedQueue = queue(durableStore, restartedTransport) {
            assertNotNull(leased.leaseUntilEpochMilliseconds)
        }
        restartedQueue.processDue()

        assertEquals(listOf(1), restartedTransport.attempts)
        assertNull(durableStore.pending(deliveryId))
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
    fun `processDue reports how many deliveries it claimed`() = runTest {
        val transport = RecordingTransport()
        val queue = queue(
            store = FileWebhookDeliveryStore(temporaryDirectory()),
            transport = transport,
            clock = { 1_000L },
            maxConcurrentDeliveries = 2,
        )
        repeat(5) { index -> queue.enqueue(update(event = "event-$index"), webhook()) }

        assertEquals(2, queue.processDue())
        assertEquals(2, queue.processDue())
        assertEquals(1, queue.processDue())
        assertEquals(0, queue.processDue())
        assertEquals(5, transport.deliveries.size)
    }

    @Test
    fun `worker drains a full backlog without waiting for the poll interval`() = runBlocking {
        val delivered = AtomicInteger()
        val transport = object : WebhookTransport {
            override suspend fun deliver(
                delivery: WebhookDelivery,
                attempt: Int,
                timestampEpochSeconds: Long,
            ): WebhookAttemptResult {
                delivered.incrementAndGet()
                return WebhookAttemptResult(statusCode = 200)
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val queue = WebhookDeliveryQueue(
            store = FileWebhookDeliveryStore(temporaryDirectory()),
            transport = transport,
            clock = { 1_000L },
            jitter = { 0 },
            pollIntervalMilliseconds = 30_000,
            maxConcurrentDeliveries = 2,
            scope = scope,
        )
        try {
            repeat(4) { index -> queue.enqueue(update(event = "event-$index"), webhook()) }
            queue.start()
            withTimeout(5_000) {
                while (delivered.get() < 4) delay(20)
            }
        } finally {
            queue.stop()
            scope.cancel()
        }
        assertEquals(4, delivered.get())
    }

    @Test
    fun `slow receivers are processed with bounded concurrency`() = runTest {
        val started = Channel<Unit>(Channel.UNLIMITED)
        val release = CompletableDeferred<Unit>()
        val inFlight = AtomicInteger()
        val maximumInFlight = AtomicInteger()
        val transport = object : WebhookTransport {
            override suspend fun deliver(
                delivery: WebhookDelivery,
                attempt: Int,
                timestampEpochSeconds: Long,
            ): WebhookAttemptResult {
                val active = inFlight.incrementAndGet()
                maximumInFlight.updateAndGet { current -> maxOf(current, active) }
                started.send(Unit)
                try {
                    release.await()
                } finally {
                    inFlight.decrementAndGet()
                }
                return WebhookAttemptResult(statusCode = 204)
            }
        }
        val queue = queue(
            store = FileWebhookDeliveryStore(temporaryDirectory()),
            transport = transport,
            clock = { 1_000L },
            maxConcurrentDeliveries = 3,
        )
        repeat(6) { index -> queue.enqueue(update(event = "event-$index"), webhook()) }

        val processing = async { queue.processDue() }
        repeat(3) { started.receive() }

        assertEquals(3, maximumInFlight.get())
        assertTrue(started.tryReceive().isFailure)
        release.complete(Unit)
        processing.await()
        assertEquals(0, inFlight.get())
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
    fun `retry jitter scales with the capped backoff`() = runTest {
        val store = FileWebhookDeliveryStore(temporaryDirectory())
        var jitterUpperBound = 0L
        val queue = queue(
            store = store,
            transport = RecordingTransport(WebhookAttemptResult(statusCode = 503)),
            clock = { 3_000L },
            jitter = { upperExclusive ->
                jitterUpperBound = upperExclusive
                upperExclusive - 1
            },
        )
        val deliveryId = queue.enqueue(
            update(),
            webhook(
                maxAttempts = 2,
                initialBackoffSeconds = 20,
                maxBackoffSeconds = 60,
            ),
        )

        queue.processDue()

        assertEquals(10_001L, jitterUpperBound)
        assertEquals(33_000L, assertNotNull(store.pending(deliveryId)).nextAttemptAtEpochMilliseconds)
    }

    @Test
    fun `large retry delays never wrap into the past`() = runTest {
        val store = FileWebhookDeliveryStore(temporaryDirectory())
        val queue = queue(
            store = store,
            transport = RecordingTransport(WebhookAttemptResult(statusCode = 503)),
            jitter = { it - 1 },
            clock = { 3_000L },
        )
        val id = queue.enqueue(update(), webhook(initialBackoffSeconds = Long.MAX_VALUE))
        queue.processDue()
        assertEquals(Long.MAX_VALUE, assertNotNull(store.pending(id)).nextAttemptAtEpochMilliseconds)
        assertEquals(0, queue.processDue())
    }

    @Test
    fun `transport accepts HTTP dates and saturates large Retry-After values`() = runTest {
        val values = ArrayDeque(listOf("Thu, 01 Jan 1970 00:02:10 GMT", Long.MAX_VALUE.toString(), "9".repeat(20)))
        HttpClient(MockEngine {
            respond("", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, values.removeFirst()))
        }).use { client ->
            val transport = KtorWebhookTransport(client) { 123_000L }
            val delivery = delivery("{}")
            assertEquals(7_000L, transport.deliver(delivery, 1, 123).retryAfterMilliseconds)
            assertEquals(Long.MAX_VALUE, transport.deliver(delivery, 2, 123).retryAfterMilliseconds)
            assertEquals(Long.MAX_VALUE, transport.deliver(delivery, 3, 123).retryAfterMilliseconds)
        }
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
            assertEquals(delivery.enqueueOrder.toString(), request.headers[WEBHOOK_SEQUENCE_HEADER])
            assertEquals("123", request.headers[WEBHOOK_TIMESTAMP_HEADER])
            assertEquals("2", request.headers[WEBHOOK_ATTEMPT_HEADER])
            assertEquals(delivery.contentDigest, request.headers[CONTENT_DIGEST_HEADER])
            assertNull(request.headers[WEBHOOK_SIGNATURE_HEADER])
            assertEquals(payload, request.body.toByteArray().decodeToString())
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

    @Test
    fun `signed attempt authenticates the raw body and delivery metadata`() = runTest {
        val secret = "a shared secret with sufficient entropy"
        val payload = """{"event":"completed"}"""
        val normalizedUrl = Url("https://receiver.example:443/webhook?source=issuer2").toString()
        assertEquals("https://receiver.example/webhook?source=issuer2", normalizedUrl)
        val delivery = delivery(payload).copy(
            url = normalizedUrl,
            signingSecret = secret,
            bearerToken = "receiver-token",
        )
        val client = HttpClient(MockEngine { request ->
            val signature = assertNotNull(request.headers[WEBHOOK_SIGNATURE_HEADER])
            assertEquals("v1=BxDWiSo/4VW6835pqxCMkbLoSmLa4VaJvDCKe54vXCA=", signature)
            assertEquals("Bearer receiver-token", request.headers[HttpHeaders.Authorization])
            assertTrue(WebhookSignature.verify(
                signature, secret, delivery.url, delivery.deliveryId, 123, 2, expectedDigest(request.body.toByteArray().decodeToString())
            ))
            assertFalse(WebhookSignature.verify(
                signature.removePrefix("v1="), secret, delivery.url, delivery.deliveryId, 123, 2, delivery.contentDigest
            ))
            assertFalse(WebhookSignature.verify(
                "v1=invalid!", secret, delivery.url, delivery.deliveryId, 123, 2, delivery.contentDigest
            ))
            assertFalse(WebhookSignature.verify(
                signature, "a different secret with sufficient entropy", delivery.url, delivery.deliveryId, 123, 2, delivery.contentDigest
            ))
            assertFalse(WebhookSignature.verify(
                signature, secret, delivery.url, delivery.deliveryId, 123, 2, expectedDigest("tampered")
            ))
            assertFalse(WebhookSignature.verify(
                signature, secret, delivery.url, delivery.deliveryId, 123, 3, delivery.contentDigest
            ))
            assertFalse(WebhookSignature.verify(
                signature, secret, "https://other.example/webhook", delivery.deliveryId, 123, 2, delivery.contentDigest
            ))
            respond("", HttpStatusCode.NoContent)
        })

        assertEquals(204, KtorWebhookTransport(client).deliver(delivery, 2, 123).statusCode)
        client.close()
    }

    @Test
    fun `signing secret survives outbox restart and unsigned callbacks stay unsigned`() = runTest {
        val root = temporaryDirectory()
        val store = FileWebhookDeliveryStore(root)
        val queue = queue(store, RecordingTransport()) { 1_000L }
        val secret = "a shared secret with sufficient entropy"
        val signedId = queue.enqueue(update(), webhook().copy(signingSecret = secret))
        val unsignedId = queue.enqueue(update(), webhook())

        assertEquals(secret, FileWebhookDeliveryStore(root).pending(signedId)?.signingSecret)
        assertNull(store.pending(unsignedId)?.signingSecret)
    }

    @Test
    fun `erasure covers pending leased and failed callbacks for only the owning session`() = runTest {
        val store = FileWebhookDeliveryStore(temporaryDirectory())
        val queue = queue(store, RecordingTransport(WebhookAttemptResult(statusCode = 400))) { 1_000L }
        val failed = queue.enqueue(update(), webhook(), sessionOwner = "verifier-a.session")
        queue.processDue()
        val pending = queue.enqueue(update(), webhook(), sessionOwner = "verifier-a.session")
        val stale = store.claimDue(1_000L, 2_000L).single()
        val other = queue.enqueue(update(), webhook(), sessionOwner = "verifier-b.session")

        queue.eraseSession("verifier-a.session")
        assertNull(store.pending(pending))
        assertFalse(store.moveToDeadLetter(stale, stale.delivery.copy(failedAtEpochMilliseconds = 1_000, lastError = "late")))
        assertTrue(store.deadLetters().isEmpty())
        assertFalse(queue.redrive(failed))
        assertNotNull(store.pending(other))
    }

    @Test
    fun `producer holding an old snapshot cannot enqueue after erasure or restart`() = runTest {
        val directory = temporaryDirectory()
        val store = FileWebhookDeliveryStore(directory)
        val transport = RecordingTransport()
        val queue = queue(store, transport) { 1_000L }
        val owner = "verifier.session"
        val oldUpdate = update()
        val resumeProducer = CompletableDeferred<Unit>()
        val late = async(start = CoroutineStart.UNDISPATCHED) {
            resumeProducer.await()
            queue.enqueue(oldUpdate, webhook(), sessionOwner = owner)
        }
        assertFalse(queue.isSessionDeleted(owner))
        val purged = queue.eraseSession(owner, allowFutureCallbacks = true)
        resumeProducer.complete(Unit)
        assertNull(store.pending(late.await()))

        val restarted = FileWebhookDeliveryStore(directory)
        val resumed = queue(restarted, transport) { 1_000L }
        assertNull(restarted.pending(resumed.enqueue(oldUpdate, webhook(), sessionOwner = owner)))
        val redacted = resumed.enqueue(update("data_retention_purge"), webhook(), owner, sessionGeneration = purged)
        assertNotNull(restarted.pending(redacted))
        assertFalse(resumed.isSessionDeleted(owner))
        resumed.eraseSession(owner)
        assertTrue(FileWebhookDeliveryStore(directory).isSessionDeleted(owner))
        assertNull(restarted.pending(redacted))
        assertNull(restarted.pending(resumed.enqueue(update("data_retention_purge"), webhook(), owner, sessionGeneration = purged)))
        assertEquals(0, resumed.processDue())
    }

    @Test
    fun `erasure waits for an admitted transport and rejects a previously claimed send`() = runTest {
        val store = FileWebhookDeliveryStore(temporaryDirectory())
        val admitted = CompletableDeferred<Unit>()
        val continueSend = CompletableDeferred<Unit>()
        val transport = object : WebhookTransport {
            override suspend fun deliver(delivery: WebhookDelivery, attempt: Int, timestampEpochSeconds: Long): WebhookAttemptResult {
                admitted.complete(Unit)
                continueSend.await()
                return WebhookAttemptResult(statusCode = 503)
            }
        }
        val queue = queue(store, transport) { 1_000L }
        val owner = "verifier.session"
        val id = queue.enqueue(update(), webhook(), owner)
        val sending = async { queue.processDue() }
        admitted.await()
        val stale = assertNotNull(store.pending(id))
        val erasing = async(start = CoroutineStart.UNDISPATCHED) { queue.eraseSession(owner) }
        assertFalse(erasing.isCompleted)
        continueSend.complete(Unit)
        sending.await()
        erasing.await()
        assertNull(store.pending(id))
        store.dispatch(WebhookDeliveryClaim(stale, requireNotNull(stale.leaseId))) { error("Revoked send started") }
    }

    @Test
    fun `non-persisted results retry transient failure and discard the body on exhaustion`() = runTest {
        val store = FileWebhookDeliveryStore(temporaryDirectory())
        val unavailable = WebhookAttemptResult(statusCode = 503)
        val queue = queue(store, RecordingTransport(unavailable, unavailable)) { 1_000L }
        val id = queue.enqueue(update(), webhook(maxAttempts = 2), "verifier.session", retainOnFailure = false)
        queue.processDue()
        assertEquals(1, assertNotNull(store.pending(id)).attemptCount)
        queue.processDue()
        assertNull(store.pending(id))
        assertTrue(queue.deadLetters().isEmpty())
        assertFalse(queue.redrive(id))
    }

    private fun temporaryDirectory(): Path =
        Files.createTempDirectory("webhook-delivery-test-").also(temporaryDirectories::add)

    private fun queue(
        store: WebhookDeliveryStore,
        transport: WebhookTransport,
        jitter: (Long) -> Long = { 0 },
        maxConcurrentDeliveries: Int = 4,
        clock: () -> Long,
    ) = WebhookDeliveryQueue(
        store = store,
        transport = transport,
        clock = clock,
        jitter = jitter,
        maxConcurrentDeliveries = maxConcurrentDeliveries,
    )

    private fun update(event: String = "completed") = KtorSessionUpdate(
        target = "session-1",
        event = event,
        session = buildJsonObject { put("status", "done") },
        requestId = "request-1",
    )

    private fun webhook(
        maxAttempts: Int = 5,
        initialBackoffSeconds: Long = 0,
        maxBackoffSeconds: Long = initialBackoffSeconds,
    ) =
        KtorSessionNotifications.VerificationSessionWebhookNotification(
            url = io.ktor.http.Url("https://receiver.example/webhook"),
            retryPolicy = KtorSessionNotifications.WebhookRetryPolicy(
                maxAttempts = maxAttempts,
                initialBackoffSeconds = initialBackoffSeconds,
                maxBackoffSeconds = maxBackoffSeconds,
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

    private class CrashAfterLeaseStore(
        private val delegate: WebhookDeliveryStore,
    ) : WebhookDeliveryStore by delegate {
        private var crashPending = true

        override suspend fun claimDue(
            nowEpochMilliseconds: Long,
            leaseUntilEpochMilliseconds: Long,
            limit: Int,
        ): List<WebhookDeliveryClaim> {
            val claimed = delegate.claimDue(nowEpochMilliseconds, leaseUntilEpochMilliseconds, limit)
            if (crashPending) {
                crashPending = false
                throw SimulatedCrashException()
            }
            return claimed
        }
    }

    private class SimulatedCrashException : RuntimeException()
}
