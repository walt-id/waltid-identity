package id.walt.credentials.status

import kotlinx.coroutines.CancellationException
import id.walt.policies2.vc.policies.status.StatusListContent
import id.walt.policies2.vc.policies.status.runStatusCheck
import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.SendingRequest
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.fromHttpToGmtDate
import io.ktor.util.AttributeKey
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Limits are local policy, not issuer freshness promises. */
data class CredentialStatusLimits(
    val fallbackFreshness: Duration = 15.minutes,
    val maximumFreshness: Duration = 60.minutes,
    val requestTimeout: Duration = 10.seconds,
    val maxResponseBytes: Int = 2 * 1024 * 1024,
    val maxExpandedBytes: Int = 8 * 1024 * 1024,
    val maxCachedBytes: Int = 16 * 1024 * 1024,
    val maxCachedLists: Int = 64,
    val concurrency: Int = 4,
    val resolutionTimeout: Duration = 30.seconds,
    val maxReferencesPerCredential: Int = 64,
) {
    init {
        require(fallbackFreshness.isPositive() && maximumFreshness.isPositive() && requestTimeout.isPositive())
        require(maxResponseBytes in 1..16 * 1024 * 1024)
        require(maxExpandedBytes in 1..64 * 1024 * 1024)
        require(maxCachedBytes in maxResponseBytes..64 * 1024 * 1024)
        require(maxCachedLists in 1..256 && concurrency in 1..16)
        require(resolutionTimeout.isPositive() && resolutionTimeout <= 5.minutes)
        require(maxReferencesPerCredential in 1..256)
    }
}

internal data class StatusListResource(
    val content: StatusListContent,
    val retrievedAt: Instant,
    val freshUntil: Instant,
    val storageAllowed: Boolean,
    val bodyId: String,
)

/**
 * One instance belongs to one wallet/trust partition. Byte reuse never implies signer authorization.
 * The caller owns the scope and dedicated, unauthenticated HTTP client. Redirects are rejected.
 */
internal class StatusListTransport(
    client: HttpClient,
    private val scope: CoroutineScope,
    private val now: () -> Instant,
    private val limits: CredentialStatusLimits,
) {
    // A derived client owns these final send guards without changing the caller's API client.
    private val client = client.config {
        followRedirects = false
        install(StatusRequestBoundary)
    }
    private data class Cached(
        val resource: StatusListResource,
        val bytes: ByteArray,
        val etag: String?,
        val modified: String?,
        val revalidate: Boolean,
        val cacheControl: String?,
    )
    private class Flight(val result: Deferred<Result<StatusListResource>>, var owners: Int = 0)

    private val mutex = Mutex()
    private val cache = linkedMapOf<String, Cached>()
    private val flights = mutableMapOf<String, Flight>()
    private val rateLimits = mutableMapOf<String, Instant>()
    private val permits = Semaphore(limits.concurrency)

    suspend fun fetch(uri: String, force: Boolean, session: CredentialStatusRefreshSession? = null): StatusListResource {
        requireEndpoint(uri)
        val flight = mutex.withLock {
            require(session == null || session.owner === this) { "Refresh session belongs to another resolver" }
            session?.cached(uri)?.let { return it.getOrThrow() }
            val at = now()
            if (rateLimits[uri]?.let { it > at } == true) fail(StatusFailure.RATE_LIMITED)
            rateLimits.remove(uri)
            val stored = cache[uri]
            if (!force && stored != null && !stored.revalidate &&
                at >= stored.resource.retrievedAt && at < stored.resource.freshUntil
            ) return stored.resource
            flights.getOrPut(uri) {
                Flight(scope.async(start = CoroutineStart.LAZY) {
                    runStatusCheck {
                    permits.withPermit {
                        withTimeoutOrNull(limits.requestTimeout) { download(uri) }
                            ?: fail(StatusFailure.TIMEOUT)
                    }
                    }
                })
            }.also { it.owners++ }
        }
        flight.result.start()
        return try {
            flight.result.await().getOrThrow().also { resource -> mutex.withLock { session?.remember(uri, Result.success(resource)) } }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            mutex.withLock { session?.remember(uri, Result.failure(failure)) }
            throw failure
        } finally {
            withContext(NonCancellable) { mutex.withLock {
                flight.owners--
                if (flight.owners == 0 && flights[uri] === flight) {
                    flights.remove(uri)
                    if (!flight.result.isCompleted) flight.result.cancel()
                }
            } }
        }
    }

    fun refreshSession(): CredentialStatusRefreshSession = CredentialStatusRefreshSession(this, now, limits)

    suspend fun constrain(uri: String, bodyId: String, deadline: Instant) = mutex.withLock {
        cache[uri]?.takeIf { it.resource.bodyId == bodyId }?.let {
            if (deadline < it.resource.freshUntil) cache[uri] = it.copy(resource = it.resource.copy(freshUntil = deadline))
        }
    }

    suspend fun invalidate(uri: String, bodyId: String) = mutex.withLock {
        if (cache[uri]?.resource?.bodyId == bodyId) cache.remove(uri)
    }

    suspend fun clear() = mutex.withLock {
        flights.values.forEach { it.result.cancel() }
        flights.clear()
        cache.clear()
        rateLimits.clear()
    }

    private suspend fun download(uri: String): StatusListResource {
        val previous = mutex.withLock { cache[uri] }
        var conditional = previous != null
        repeat(2) {
            val startedAt = now()
            val response = client.get(uri) {
                attributes.put(StatusOriginalUrl, Url(uri))
                header(HttpHeaders.Accept, "application/statuslist+jwt, application/statuslist+cwt, application/vc+jwt")
                header(HttpHeaders.CacheControl, "no-cache")
                // A status-list request must never inherit the wallet API's credentials.
                headers.remove(HttpHeaders.Authorization)
                headers.remove(HttpHeaders.Cookie)
                if (conditional) {
                    previous?.etag?.let { header(HttpHeaders.IfNoneMatch, it) }
                    previous?.modified?.let { header(HttpHeaders.IfModifiedSince, it) }
                }
            }
            val receivedAt = now()
            if (response.status == HttpStatusCode.TooManyRequests) {
                val wait = response.headers[HttpHeaders.RetryAfter]?.toLongOrNull()?.coerceIn(1, 3600) ?: 30
                mutex.withLock { rateLimits[uri] = receivedAt + wait.seconds }
                response.bodyAsChannel().cancel(null)
                fail(StatusFailure.RATE_LIMITED)
            }
            val notModified = response.status == HttpStatusCode.NotModified
            if (notModified && (!conditional || previous == null)) {
                response.bodyAsChannel().cancel(null)
                conditional = false
                return@repeat
            }
            if (!notModified && response.status.value !in 200..299) {
                response.bodyAsChannel().cancel(null)
                fail(StatusFailure.TRANSPORT)
            }
            val bytes = if (notModified) {
                response.bodyAsChannel().cancel(null)
                requireNotNull(previous).bytes
            } else {
                val declared = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                if (declared != null && declared > limits.maxResponseBytes) {
                    response.bodyAsChannel().cancel(null)
                    fail(StatusFailure.RESOURCE_LIMIT)
                }
                val channel = response.bodyAsChannel()
                val chunks = mutableListOf<ByteArray>()
                var size = 0
                val buffer = ByteArray(4096)
                try {
                    while (true) {
                        val count = channel.readAvailable(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        if (count > limits.maxResponseBytes - size) fail(StatusFailure.RESOURCE_LIMIT)
                        chunks += buffer.copyOf(count)
                        size += count
                    }
                } finally { channel.cancel(null) }
                ByteArray(size).also { result ->
                    var offset = 0
                    chunks.forEach { chunk -> chunk.copyInto(result, offset); offset += chunk.size }
                }
            }
            val controls = response.headers.getAll(HttpHeaders.CacheControl)?.joinToString(",")
                ?: if (notModified) previous?.cacheControl else null
            val directives = controls.orEmpty().lowercase().split(',').map(String::trim)
            val vary = response.headers.getAll(HttpHeaders.Vary).orEmpty()
                .flatMap { it.lowercase().split(',').map(String::trim) }
            val storageAllowed = directives.none { it.substringBefore('=') == "no-store" } &&
                vary.all { it in setOf("accept", "accept-encoding") }
            val revalidate = directives.any { it.substringBefore('=') == "no-cache" }
            val ages = directives.filter { it.startsWith("max-age=") }
            val maxAge = if (ages.isEmpty()) null else if (ages.size != 1) 0L else
                ages.single().substringAfter('=').trim('"').toLongOrNull()?.coerceAtLeast(0) ?: 0L
            val date = response.headers[HttpHeaders.Date]?.let {
                runCatching { Instant.fromEpochMilliseconds(it.fromHttpToGmtDate().timestamp) }.getOrNull()
            }
            val apparentAge = date?.let { (receivedAt - it).coerceAtLeast(Duration.ZERO) } ?: Duration.ZERO
            val correctedAge = maxOf(apparentAge,
                (response.headers[HttpHeaders.Age]?.toLongOrNull()?.coerceAtLeast(0) ?: 0).seconds +
                    (receivedAt - startedAt).coerceAtLeast(Duration.ZERO))
            val expires = response.headers[HttpHeaders.Expires]?.let {
                runCatching { Instant.fromEpochMilliseconds(it.fromHttpToGmtDate().timestamp) }.getOrNull()
            }
            val lifetime = minOf(maxAge?.seconds ?: expires?.let { (it - (date ?: receivedAt)).coerceAtLeast(Duration.ZERO) }
                ?: limits.fallbackFreshness, limits.maximumFreshness)
            val freshUntil = if (revalidate || !storageAllowed) receivedAt else
                receivedAt + (lifetime - correctedAge).coerceAtLeast(Duration.ZERO)
            val content = if (notModified) requireNotNull(previous).resource.content else {
                if (response.contentType()?.contentSubtype == "statuslist+cwt") StatusListContent.Binary(bytes)
                else StatusListContent.Text(bytes.decodeToString())
            }
            val resource = StatusListResource(content, receivedAt, freshUntil, storageAllowed, fingerprint(bytes))
            mutex.withLock {
                cache.remove(uri)
                if (storageAllowed) {
                    cache[uri] = Cached(resource, bytes,
                        response.headers[HttpHeaders.ETag] ?: if (notModified) previous?.etag else null,
                        response.headers[HttpHeaders.LastModified] ?: if (notModified) previous?.modified else null,
                        revalidate, controls)
                    while (cache.size > limits.maxCachedLists || cache.values.sumOf { it.bytes.size.toLong() } > limits.maxCachedBytes) {
                        cache.remove(cache.keys.first())
                    }
                }
            }
            return resource
        }
        fail(StatusFailure.TRANSPORT)
    }

    private fun requireEndpoint(uri: String) {
        val url = try { Url(uri) } catch (_: IllegalArgumentException) { fail(StatusFailure.REFERENCE_MALFORMED) }
        if (url.protocol != URLProtocol.HTTPS || !url.user.isNullOrEmpty() || !url.password.isNullOrEmpty() || url.fragment.isNotEmpty()) {
            fail(StatusFailure.REFERENCE_MALFORMED)
        }
    }

    fun close() { client.close() }
}

private val StatusOriginalUrl = AttributeKey<Url>("credential-status-original-url")
private val StatusRequestBoundary = createClientPlugin("CredentialStatusRequestBoundary") {
    on(SendingRequest) { request, _ ->
        if (request.url.build() != request.attributes[StatusOriginalUrl]) fail(StatusFailure.TRANSPORT)
        request.headers.remove(HttpHeaders.Authorization)
        request.headers.remove(HttpHeaders.Cookie)
        request.headers.remove(HttpHeaders.ProxyAuthorization)
    }
}

internal fun fail(reason: StatusFailure): Nothing = throw CredentialStatusException(reason)

internal fun fingerprint(bytes: ByteArray): String = org.kotlincrypto.hash.sha2.SHA256().digest(bytes).toHexString()
