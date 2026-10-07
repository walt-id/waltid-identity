package id.walt.policies2.vc.policies.status

import id.walt.cose.CoseSign1
import id.walt.cose.coseCompliantCbor
import id.walt.policies2.vc.policies.status.content.JwtParser
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.CborLabel
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Represents fetched status list content, supporting both text (JWT) and binary (CWT) formats.
 */
sealed class StatusListContent {
    /**
     * Text-based content (e.g., JWT status lists).
     */
    data class Text(val content: String) : StatusListContent()

    /**
     * Binary content (e.g., CWT status lists as raw CBOR bytes).
     */
    data class Binary(val content: ByteArray) : StatusListContent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Binary) return false
            return content.contentEquals(other.content)
        }

        override fun hashCode(): Int = content.contentHashCode()
    }
}

/**
 * Fetches status-list tokens and reuses a body until the earliest of HTTP freshness and the
 * token `ttl`/`exp` claims. Missing bounds, `no-store`, `no-cache`, or a non-positive remaining
 * lifetime keep the previous always-fetch behavior.
 */
class CredentialFetcher(
    private val client: HttpClient,
    private val now: () -> Instant,
) {
    constructor(client: HttpClient) : this(client, { Clock.System.now() })

    private val logger = KotlinLogging.logger { }
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, CachedStatusList>()

    /**
     * Fetches status list content from the given URL.
     * Returns [StatusListContent.Binary] for CWT content types and [StatusListContent.Text] for others.
     */
    suspend fun fetch(url: String): Result<StatusListContent> = runCatching {
        cached(url)?.let { cached ->
            logger.debug { "Using cached status list from URL: $url" }
            return@runCatching cached
        }
        logger.debug { "Fetching content from URL: $url" }
        val requestStart = now()
        val response = download(url)
        val receivedAt = now()
        val contentType = response.contentType()
        val content = if (contentType?.match(ContentType("application", "statuslist+cwt")) == true) {
            logger.debug { "Received binary CWT content" }
            StatusListContent.Binary(response.readRawBytes())
        } else {
            logger.debug { "Received text content" }
            StatusListContent.Text(response.bodyAsText())
        }
        remember(
            url = url,
            content = content,
            cacheControlValues = response.headers.getAll(HttpHeaders.CacheControl).orEmpty(),
            ageSeconds = response.headers[HttpHeaders.Age]?.toLongOrNull(),
            responseDelay = receivedAt - requestStart,
        )
        content
    }.onFailure { logger.error { "Failed to fetch content from URL: $url" } }

    private suspend fun download(url: String): HttpResponse {
        val response = client.get(url) {
            headers {
                append(HttpHeaders.Accept, "application/statuslist+jwt, application/statuslist+cwt, text/plain, */*")
            }
        }
        return response.takeIf { it.status.isSuccess() }
            ?: throw IllegalStateException("URL $url returned unexpected status: ${response.status}")
    }

    private suspend fun cached(url: String): StatusListContent? = mutex.withLock {
        val entry = cache[url] ?: return null
        if (now() < entry.expiresAt) {
            entry.content
        } else {
            cache.remove(url)
            null
        }
    }

    private suspend fun remember(
        url: String,
        content: StatusListContent,
        cacheControlValues: List<String>,
        ageSeconds: Long?,
        responseDelay: Duration,
    ) {
        val currentTime = now()
        val (ttlSeconds, expEpochSeconds) = StatusListTokenLifetime.from(content)
        val expiresAt = StatusListCacheExpiry.of(
            now = currentTime,
            httpRemaining = HttpCacheControl.remainingFreshness(cacheControlValues, ageSeconds, responseDelay),
            ttlSeconds = ttlSeconds,
            expEpochSeconds = expEpochSeconds,
        ) ?: return
        mutex.withLock {
            cache.entries.removeAll { it.value.expiresAt <= currentTime }
            cache[url] = CachedStatusList(content, expiresAt)
            while (cache.size > MAX_CACHE_ENTRIES) {
                val oldest = cache.minBy { it.value.expiresAt }
                cache.remove(oldest.key)
            }
        }
    }

    private data class CachedStatusList(
        val content: StatusListContent,
        val expiresAt: Instant,
    )

    private companion object {
        const val MAX_CACHE_ENTRIES = 256
    }
}

internal object HttpCacheControl {
    fun remainingFreshness(
        cacheControlValues: List<String>,
        ageSeconds: Long?,
        responseDelay: Duration,
    ): Duration? {
        val combined = cacheControlValues.joinToString(",")
        val maxAge = maxAgeSeconds(combined) ?: return null
        val age = (ageSeconds ?: 0L).coerceAtLeast(0L)
        val delaySeconds = responseDelay.inWholeSeconds.coerceAtLeast(0L)
        val remaining = maxAge - age - delaySeconds
        return remaining.takeIf { it > 0 }?.seconds
    }

    fun maxAgeSeconds(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val directives = header.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (directives.any { it == "no-store" || it == "no-cache" || it.startsWith("no-store") || it.startsWith("no-cache") }) {
            return null
        }
        val maxAge = directives.firstNotNullOfOrNull { directive ->
            directive.takeIf { it.startsWith("max-age=") }
                ?.substringAfter("=")
                ?.substringBefore(';')
                ?.toLongOrNull()
        }
        return maxAge?.takeIf { it > 0 }
    }
}

internal object StatusListCacheExpiry {
    fun of(
        now: Instant,
        httpRemaining: Duration?,
        ttlSeconds: Long?,
        expEpochSeconds: Long?,
    ): Instant? {
        val deadlines = buildList {
            httpRemaining?.let { add(now + it) }
            ttlSeconds?.takeIf { it > 0 }?.let { add(now + it.seconds) }
            expEpochSeconds?.let { add(Instant.fromEpochSeconds(it)) }
        }
        return deadlines.minOrNull()?.takeIf { it > now }
    }
}

internal object StatusListTokenLifetime {
    private val jwtParser = JwtParser()

    fun from(content: StatusListContent): Pair<Long?, Long?> = when (content) {
        is StatusListContent.Text -> fromJwt(content.content)
        is StatusListContent.Binary -> fromCwt(content.content)
    }

    fun fromJwt(jwt: String): Pair<Long?, Long?> = runCatching {
        val payload = jwtParser.parse(jwt)
        payload["ttl"]?.jsonPrimitive?.longOrNull to payload["exp"]?.jsonPrimitive?.longOrNull
    }.getOrDefault(null to null)

    fun fromCwt(cwt: ByteArray): Pair<Long?, Long?> = runCatching {
        val payload = CoseSign1.fromTagged(cwt).payload ?: return null to null
        fromCwtPayload(payload)
    }.getOrDefault(null to null)

    @OptIn(ExperimentalSerializationApi::class)
    fun fromCwtPayload(payload: ByteArray): Pair<Long?, Long?> = runCatching {
        val claims = coseCompliantCbor.decodeFromByteArray<CwtLifetimeClaims>(payload)
        claims.ttl to claims.exp
    }.getOrDefault(null to null)

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    private data class CwtLifetimeClaims(
        @CborLabel(4) val exp: Long? = null,
        @CborLabel(65534) val ttl: Long? = null,
    )
}
