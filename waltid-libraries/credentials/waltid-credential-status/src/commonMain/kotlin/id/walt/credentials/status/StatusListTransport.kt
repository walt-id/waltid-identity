package id.walt.credentials.status

import kotlinx.coroutines.CancellationException
import id.walt.policies2.vc.policies.status.StatusListContent
import id.walt.policies2.vc.policies.status.runStatusCheck
import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.SendingRequest
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.prepareGet
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.fromHttpToGmtDate
import io.ktor.util.date.GMTDate
import io.ktor.util.date.GMTDateParser
import io.ktor.util.AttributeKey
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray
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
import org.kotlincrypto.hash.sha2.SHA256

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

internal enum class StatusListRepresentation(val accept: String) {
    TOKEN_JWT("application/statuslist+jwt"),
    TOKEN_CWT("application/statuslist+cwt"),
    TOKEN_JWT_OR_CWT("application/statuslist+jwt, application/statuslist+cwt"),
    VC_JWT("application/vc+jwt"),
}

/** Negotiated bytes and their validators belong to both the endpoint and the requested representation. */
internal data class StatusListRequest(val uri: String, val representation: StatusListRepresentation)

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
        val expires: String?,
    )
    private class Flight(val result: Deferred<Result<StatusListResource>>, var owners: Int = 0)

    private val mutex = Mutex()
    private val cache = linkedMapOf<StatusListRequest, Cached>()
    private val flights = mutableMapOf<StatusListRequest, Flight>()
    private val rateLimits = mutableMapOf<String, Instant>()
    private var overflowRetryAt: Instant? = null
    private val permits = Semaphore(limits.concurrency)

    suspend fun fetch(request: StatusListRequest, force: Boolean, session: CredentialStatusRefreshSession? = null): StatusListResource {
        requireStatusEndpoint(request.uri)
        val flight = mutex.withLock {
            require(session == null || session.owner === this) { "Refresh session belongs to another resolver" }
            session?.cached(request)?.let { return it.getOrThrow() }
            val at = now()
            rateLimits.entries.removeAll { it.value <= at }
            rateLimits[request.uri]?.let { fail(CheckFailure.RateLimited(it)) }
            val stored = cache[request]
            if (!force && stored != null && !stored.revalidate &&
                at >= stored.resource.retrievedAt && at < stored.resource.freshUntil
            ) return stored.resource
            overflowRetryAt?.takeIf { it > at }?.let { fail(CheckFailure.RateLimited(it)) }
            overflowRetryAt = null
            flights.getOrPut(request) {
                Flight(scope.async(start = CoroutineStart.LAZY) {
                    runStatusCheck {
                    permits.withPermit {
                        withTimeoutOrNull(limits.requestTimeout) { download(request) }
                            ?: fail(CheckFailure.Reason.TIMEOUT)
                    }
                    }
                })
            }.also { it.owners++ }
        }
        flight.result.start()
        return try {
            flight.result.await().getOrThrow().also { resource -> mutex.withLock { session?.remember(request, Result.success(resource)) } }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            mutex.withLock { session?.remember(request, Result.failure(failure)) }
            throw failure
        } finally {
            withContext(NonCancellable) { mutex.withLock {
                flight.owners--
                if (flight.owners == 0 && flights[request] === flight) {
                    flights.remove(request)
                    if (!flight.result.isCompleted) flight.result.cancel()
                }
            } }
        }
    }

    fun refreshSession(): CredentialStatusRefreshSession = CredentialStatusRefreshSession(this, now, limits)

    suspend fun constrain(request: StatusListRequest, bodyId: String, deadline: Instant) = mutex.withLock {
        cache[request]?.takeIf { it.resource.bodyId == bodyId }?.let {
            if (deadline < it.resource.freshUntil) cache[request] = it.copy(resource = it.resource.copy(freshUntil = deadline))
        }
    }

    suspend fun invalidate(request: StatusListRequest, bodyId: String) = mutex.withLock {
        if (cache[request]?.resource?.bodyId == bodyId) cache.remove(request)
    }

    suspend fun clear() = mutex.withLock {
        flights.values.forEach { it.result.cancel() }
        flights.clear()
        cache.clear()
        rateLimits.clear()
        overflowRetryAt = null
    }

    private suspend fun download(request: StatusListRequest): StatusListResource {
        val uri = request.uri
        val previous = mutex.withLock { cache[request] }
        var conditional = previous != null
        repeat(2) {
            val startedAt = now()
            val resource = client.prepareGet(uri) {
                attributes.put(StatusOriginalUrl, Url(uri))
                header(HttpHeaders.Accept, request.representation.accept)
                header(HttpHeaders.CacheControl, "no-cache")
                // A status-list request must never inherit the wallet API's credentials.
                headers.remove(HttpHeaders.Authorization)
                headers.remove(HttpHeaders.Cookie)
                if (conditional) {
                    previous?.etag?.let { header(HttpHeaders.IfNoneMatch, it) }
                    previous?.modified?.let { header(HttpHeaders.IfModifiedSince, it) }
                }
            }.execute { response ->
                val receivedAt = now()
                if (response.status == HttpStatusCode.TooManyRequests) {
                    val retryAfter = response.headers[HttpHeaders.RetryAfter]
                    val wait = retryAfter?.toLongOrNull()?.seconds
                        ?: retryAfter?.toRetryAfterInstant(receivedAt)?.minus(receivedAt)
                        ?: 30.seconds
                    val retryAt = receivedAt + wait.coerceIn(1.seconds, 60.minutes)
                    mutex.withLock {
                        rateLimits.entries.removeAll { it.value <= receivedAt }
                        if (uri in rateLimits || rateLimits.size < limits.maxCachedLists) rateLimits[uri] = retryAt
                        // Concurrent responses can fill the table. Preserve their deadlines with bounded, conservative backoff.
                        else overflowRetryAt = maxOf(overflowRetryAt ?: retryAt, retryAt)
                    }
                    fail(CheckFailure.RateLimited(retryAt))
                }
                val notModified = response.status == HttpStatusCode.NotModified
                if (notModified && (!conditional || previous == null)) {
                    conditional = false
                    return@execute null
                }
                if (!notModified && response.status.value !in 200..299) {
                    fail(CheckFailure.Reason.TRANSPORT)
                }
                val bytes = if (notModified) {
                    requireNotNull(previous).bytes
                } else {
                    val declared = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                    if (declared != null && declared > limits.maxResponseBytes) fail(CheckFailure.Reason.RESOURCE_LIMIT)
                    val data = response.bodyAsChannel().readBuffer(limits.maxResponseBytes + 1L).readByteArray()
                    if (data.size > limits.maxResponseBytes) fail(CheckFailure.Reason.RESOURCE_LIMIT)
                    data
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
                val expiresHeader = response.headers[HttpHeaders.Expires]
                    ?: if (notModified) previous?.expires else null
                val expires = expiresHeader?.let {
                    runCatching { Instant.fromEpochMilliseconds(it.fromHttpToGmtDate().timestamp) }.getOrNull()
                }
                val lifetime = minOf(maxAge?.seconds ?: expires?.let { (it - (date ?: receivedAt)).coerceAtLeast(Duration.ZERO) }
                    ?: limits.fallbackFreshness, limits.maximumFreshness)
                // no-cache requires another HTTP validation, not immediate expiry of the observation.
                val freshUntil = if (!storageAllowed) receivedAt else
                    receivedAt + (lifetime - correctedAge).coerceAtLeast(Duration.ZERO)
                val content = if (notModified) requireNotNull(previous).resource.content else {
                    if (response.contentType()?.contentSubtype == "statuslist+cwt") StatusListContent.Binary(bytes)
                    else StatusListContent.Text(bytes.decodeToString())
                }
                val resource = StatusListResource(content, receivedAt, freshUntil, storageAllowed, fingerprint(bytes))
                mutex.withLock {
                    cache.remove(request)
                    if (storageAllowed) {
                        cache[request] = Cached(resource, bytes,
                            response.headers[HttpHeaders.ETag] ?: if (notModified) previous?.etag else null,
                            response.headers[HttpHeaders.LastModified] ?: if (notModified) previous?.modified else null,
                            revalidate, controls, expiresHeader)
                        while (cache.size > limits.maxCachedLists || cache.values.sumOf { it.bytes.size.toLong() } > limits.maxCachedBytes) {
                            cache.remove(cache.keys.first())
                        }
                    }
                }
                resource
            }
            if (resource != null) return resource
        }
        fail(CheckFailure.Reason.TRANSPORT)
    }


    fun close() { client.close() }
}

private val Rfc850Date = Regex("""[A-Za-z]+, (\d{2}-[A-Za-z]{3}-)(\d{2})( \d{2}:\d{2}:\d{2} GMT)""")
private val Rfc850DateParser = GMTDateParser("dd-MMM-YYYY hh:mm:ss zzz")
private val AsctimeDateParser = GMTDateParser("*** MMM dd hh:mm:ss YYYY")

private fun String.toRetryAfterInstant(receivedAt: Instant): Instant? = runCatching {
    val value = trim()
    val rfc850 = Rfc850Date.matchEntire(value)
    val date = when {
        rfc850 != null -> {
            val (dayAndMonth, shortYear, timeAndZone) = rfc850.destructured
            val received = GMTDate(receivedAt.toEpochMilliseconds())
            // RFC 9110 limits the two-digit year to 50 years in the future.
            val latestYear = received.year + 50
            val year = latestYear - (latestYear - shortYear.toInt()).mod(100)
            fun dateIn(year: Int) = Rfc850DateParser.parse("$dayAndMonth${year.toString().padStart(4, '0')}$timeAndZone")
            val candidate = dateIn(year)
            val beyondWindow = year == latestYear && compareValuesBy(
                candidate, received, GMTDate::month, GMTDate::dayOfMonth,
                GMTDate::hours, GMTDate::minutes, GMTDate::seconds,
            ) > 0
            if (beyondWindow) dateIn(year - 100) else candidate
        }
        ',' !in value -> AsctimeDateParser.parse(value.replace("  ", " 0"))
        else -> value.fromHttpToGmtDate()
    }
    Instant.fromEpochMilliseconds(date.timestamp)
}.getOrNull()

private val StatusOriginalUrl = AttributeKey<Url>("credential-status-original-url")
private val StatusRequestBoundary = createClientPlugin("CredentialStatusRequestBoundary") {
    on(SendingRequest) { request, _ ->
        if (request.url.build() != request.attributes[StatusOriginalUrl]) fail(CheckFailure.Reason.TRANSPORT)
        request.headers.remove(HttpHeaders.Authorization)
        request.headers.remove(HttpHeaders.Cookie)
        request.headers.remove(HttpHeaders.ProxyAuthorization)
    }
}

internal fun fail(reason: CheckFailure): Nothing = throw CredentialStatusException(reason)

internal fun fingerprint(bytes: ByteArray): String = SHA256().digest(bytes).toHexString()

internal fun requireStatusEndpoint(uri: String) {
    val url = try { Url(uri) } catch (_: IllegalArgumentException) { fail(CheckFailure.Reason.INVALID_LIST) }
    if (url.protocol != URLProtocol.HTTPS || !url.user.isNullOrEmpty() || !url.password.isNullOrEmpty() || url.fragment.isNotEmpty()) {
        fail(CheckFailure.Reason.INVALID_LIST)
    }
}
