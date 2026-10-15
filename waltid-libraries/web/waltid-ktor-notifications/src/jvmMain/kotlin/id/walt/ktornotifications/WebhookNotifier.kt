package id.walt.ktornotifications

import id.walt.ktornotifications.core.KtorSessionNotifications.VerificationSessionWebhookNotification
import id.walt.ktornotifications.core.KtorSessionUpdate
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.basicAuth
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.pow
import kotlin.random.Random

private val log = KotlinLogging.logger { }

internal interface WebhookTransport {
    suspend fun deliver(delivery: WebhookDelivery, attempt: Int, timestampEpochSeconds: Long): WebhookAttemptResult
}

internal class KtorWebhookTransport(
    private val client: HttpClient = HttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
    },
    private val nowEpochMilliseconds: () -> Long = System::currentTimeMillis,
) : WebhookTransport {
    override suspend fun deliver(
        delivery: WebhookDelivery,
        attempt: Int,
        timestampEpochSeconds: Long,
    ): WebhookAttemptResult {
        val response = client.post(delivery.url) {
            contentType(ContentType.Application.Json)
            header(WEBHOOK_ID_HEADER, delivery.deliveryId)
            header(IDEMPOTENCY_KEY_HEADER, delivery.deliveryId)
            header(WEBHOOK_TIMESTAMP_HEADER, timestampEpochSeconds)
            header(WEBHOOK_ATTEMPT_HEADER, attempt)
            header(CONTENT_DIGEST_HEADER, delivery.contentDigest)
            setBody(delivery.payload)

            if (delivery.basicAuthUser != null && delivery.basicAuthPass != null) {
                basicAuth(delivery.basicAuthUser, delivery.basicAuthPass)
            }
            if (delivery.bearerToken != null) {
                bearerAuth(delivery.bearerToken)
            }
        }
        val result = WebhookAttemptResult(
            statusCode = response.status.value,
            retryAfterMilliseconds = parseRetryAfter(
                response.headers[HttpHeaders.RetryAfter],
                nowEpochMilliseconds(),
            ),
        )
        response.bodyAsChannel().cancel(null)
        return result
    }

    private fun parseRetryAfter(value: String?, now: Long): Long? {
        if (value == null) return null
        value.toLongOrNull()?.let { seconds -> return seconds.coerceAtLeast(0) * 1_000 }
        return runCatching {
            val retryAt = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
            (retryAt - now).coerceAtLeast(0)
        }.getOrNull()
    }
}

internal class WebhookDeliveryQueue(
    private val store: WebhookDeliveryStore,
    private val transport: WebhookTransport,
    private val clock: () -> Long = System::currentTimeMillis,
    private val jitter: (Long) -> Long = { upperExclusive ->
        if (upperExclusive <= 1) 0 else Random.nextLong(upperExclusive)
    },
    private val pollIntervalMilliseconds: Long = 1_000,
    private val maxConcurrentDeliveries: Int = DEFAULT_MAX_CONCURRENT_DELIVERIES,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val wakeUp = Channel<Unit>(Channel.CONFLATED)
    private val processingMutex = Mutex()
    private val deliveryPermits = Semaphore(maxConcurrentDeliveries)
    private val enqueueSequence = AtomicLong(clock() * 1_000)
    private var worker: Job? = null

    init {
        require(maxConcurrentDeliveries > 0) { "maxConcurrentDeliveries must be greater than zero" }
    }

    @Synchronized
    fun start() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            while (isActive) {
                try {
                    processDue()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    log.error(ex) { "Webhook delivery worker failed; pending deliveries remain in the outbox" }
                }
                kotlinx.coroutines.withTimeoutOrNull(pollIntervalMilliseconds) { wakeUp.receive() }
            }
        }
    }

    suspend fun stop() {
        val activeWorker = synchronized(this) {
            worker.also { worker = null }
        }
        activeWorker?.cancel()
        activeWorker?.join()
    }

    suspend fun enqueue(
        update: KtorSessionUpdate,
        config: VerificationSessionWebhookNotification,
    ): String {
        val payload = Json.encodeToString(update)
        val now = clock()
        val delivery = WebhookDelivery(
            deliveryId = UUID.randomUUID().toString(),
            url = config.url.toString(),
            payload = payload,
            contentDigest = contentDigest(payload),
            target = update.target,
            event = update.event,
            requestId = update.requestId,
            basicAuthUser = config.basicAuthUser,
            basicAuthPass = config.basicAuthPass,
            bearerToken = config.bearerToken,
            retryPolicy = config.retryPolicy,
            enqueueOrder = enqueueSequence.updateAndGet { previous ->
                maxOf(previous + 1, now * 1_000)
            },
            createdAtEpochMilliseconds = now,
            nextAttemptAtEpochMilliseconds = now,
        )
        store.enqueue(delivery)
        wakeUp.trySend(Unit)
        return delivery.deliveryId
    }

    suspend fun processDue(limit: Int = 100) = processingMutex.withLock {
        val now = clock()
        coroutineScope {
            // Claim only work that this process can start immediately. Reserving a large backlog behind the
            // local semaphore could let leases expire before their HTTP requests begin.
            store.claimDue(
                nowEpochMilliseconds = now,
                leaseUntilEpochMilliseconds = now + DELIVERY_LEASE_MILLISECONDS,
                limit = minOf(limit, maxConcurrentDeliveries),
            ).map { claim ->
                async {
                    deliveryPermits.withPermit {
                        process(claim)
                    }
                }
            }.awaitAll()
        }
    }

    suspend fun deadLetters(): List<WebhookDeadLetter> = store.deadLetters()

    suspend fun redrive(deliveryId: String): Boolean {
        val redriven = store.redrive(deliveryId, clock())
        if (redriven) wakeUp.trySend(Unit)
        return redriven
    }

    private suspend fun process(claim: WebhookDeliveryClaim) {
        val delivery = claim.delivery
        val now = clock()
        val attempt = delivery.attemptCount + 1
        val result = try {
            transport.deliver(delivery, attempt, now / 1_000)
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            WebhookAttemptResult(error = ex)
        }
        val attempted = delivery.copy(attemptCount = attempt)

        if (result.successful) {
            if (!store.markDelivered(claim)) {
                log.warn { "Webhook success was not recorded because its lease was lost (deliveryId=${attempted.deliveryId})" }
                return
            }
            log.info {
                "Webhook delivered (deliveryId=${attempted.deliveryId}, target=${attempted.target}, " +
                    "event=${attempted.event}, attempt=$attempt, status=${result.statusCode})"
            }
            return
        }

        val error = result.failureMessage()
        val exhausted = attempt >= attempted.retryPolicy.maxAttempts
        if (!result.retryable || exhausted) {
            val moved = store.moveToDeadLetter(
                claim,
                attempted.copy(
                    failedAtEpochMilliseconds = clock(),
                    lastError = error,
                )
            )
            if (!moved) {
                log.warn { "Webhook failure was not recorded because its lease was lost (deliveryId=${attempted.deliveryId})" }
                return
            }
            log.error {
                "Webhook moved to dead-letter (deliveryId=${attempted.deliveryId}, target=${attempted.target}, " +
                    "event=${attempted.event}, attempt=$attempt): $error"
            }
            return
        }

        val retryDelay = maxOf(backoffMilliseconds(attempted), result.retryAfterMilliseconds ?: 0)
        val rescheduled = store.reschedule(
            claim,
            attempted.copy(
                nextAttemptAtEpochMilliseconds = clock() + retryDelay,
                lastError = error,
            )
        )
        if (!rescheduled) {
            log.warn { "Webhook retry was not recorded because its lease was lost (deliveryId=${attempted.deliveryId})" }
            return
        }
        log.warn {
            "Webhook delivery failed and will be retried (deliveryId=${attempted.deliveryId}, " +
                "target=${attempted.target}, event=${attempted.event}, attempt=$attempt, " +
                "retryInMs=$retryDelay): $error"
        }
    }

    private fun backoffMilliseconds(delivery: WebhookDelivery): Long {
        val policy = delivery.retryPolicy
        val base = policy.initialBackoffSeconds * 1_000.0 *
            policy.backoffMultiplier.pow((delivery.attemptCount - 1).coerceAtLeast(0))
        val capped = base.toLong().coerceAtMost(policy.maxBackoffSeconds * 1_000)
        val jitterUpperBound = capped / 2 + 1
        return capped + jitter(jitterUpperBound)
    }

    private fun contentDigest(payload: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return "sha-256=:${Base64.getEncoder().encodeToString(digest)}:"
    }

    private companion object {
        const val DELIVERY_LEASE_MILLISECONDS = 5 * 60 * 1_000L
        const val DEFAULT_MAX_CONCURRENT_DELIVERIES = 4
    }
}

object WebhookNotifier {
    private val lock = Any()

    @Volatile
    private var queue: WebhookDeliveryQueue? = null

    /** Starts the worker and resumes deliveries left pending by a previous process. */
    fun start(storageDirectory: Path = defaultStorageDirectory()) {
        queue(storageDirectory).start()
    }

    /** Starts a worker in a service-specific directory below the configured outbox root. */
    fun start(serviceName: String) {
        require(serviceName.matches(Regex("[a-zA-Z0-9_-]+"))) { "Invalid webhook delivery service name" }
        start(configuredStorageRoot().resolve(serviceName))
    }

    /** Starts the worker with a service-provided distributed outbox implementation. */
    fun start(store: WebhookDeliveryStore) {
        queue(store).start()
    }

    /**
     * Durably enqueues a webhook. Delivery happens asynchronously so protocol requests are not held open by
     * receiver backoff. The returned ID is stable for all attempts of this logical delivery.
     */
    suspend fun notify(
        update: KtorSessionUpdate,
        config: VerificationSessionWebhookNotification,
    ): String {
        val activeQueue = queue()
        activeQueue.start()
        return activeQueue.enqueue(update, config)
    }

    suspend fun deadLetters(): List<WebhookDeadLetter> = queue().deadLetters()

    suspend fun redrive(deliveryId: String): Boolean = queue().redrive(deliveryId)

    suspend fun stop() {
        val activeQueue = synchronized(lock) { queue.also { queue = null } }
        activeQueue?.stop()
    }

    private fun queue(storageDirectory: Path = defaultStorageDirectory()): WebhookDeliveryQueue =
        queue(FileWebhookDeliveryStore(storageDirectory))

    private fun queue(store: WebhookDeliveryStore): WebhookDeliveryQueue =
        queue ?: synchronized(lock) {
            queue ?: WebhookDeliveryQueue(
                store = store,
                transport = KtorWebhookTransport(),
            ).also { queue = it }
        }

    private fun defaultStorageDirectory(): Path =
        configuredStorageRoot().resolve("embedded-${ProcessHandle.current().pid()}")

    private fun configuredStorageRoot(): Path {
        val configured = System.getProperty(STORAGE_DIRECTORY_PROPERTY)
            ?: System.getenv(STORAGE_DIRECTORY_ENVIRONMENT_VARIABLE)
        if (configured != null) return Path.of(configured)

        val temporary = Path.of(System.getProperty("java.io.tmpdir"), "waltid-webhook-deliveries")
        log.warn {
            "Webhook delivery outbox is using temporary storage at $temporary. Pending and dead-letter " +
                "deliveries may be lost on restart. Configure $STORAGE_DIRECTORY_ENVIRONMENT_VARIABLE or " +
                "$STORAGE_DIRECTORY_PROPERTY on a durable, access-restricted volume for production."
        }
        return temporary
    }
}

const val WEBHOOK_ID_HEADER = "Webhook-Id"
const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
const val WEBHOOK_TIMESTAMP_HEADER = "Webhook-Timestamp"
const val WEBHOOK_ATTEMPT_HEADER = "Webhook-Attempt"
const val CONTENT_DIGEST_HEADER = "Content-Digest"

const val STORAGE_DIRECTORY_PROPERTY = "waltid.webhook.delivery.directory"
const val STORAGE_DIRECTORY_ENVIRONMENT_VARIABLE = "WALTID_WEBHOOK_DELIVERY_DIRECTORY"
