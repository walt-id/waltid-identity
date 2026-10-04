package id.walt.policies2.vc.policies.status

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
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
 * Fetches status-list tokens and, when the response includes a positive `Cache-Control: max-age`,
 * reuses that body until the lifetime elapses. Missing, `no-store`, `no-cache`, or `max-age=0`
 * headers keep the previous always-fetch behavior.
 */
class CredentialFetcher(
    private val client: HttpClient,
    private val now: () -> Instant = { Clock.System.now() },
) {
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
        val response = download(url)
        val contentType = response.contentType()
        val cacheControl = response.headers[HttpHeaders.CacheControl]
        val content = if (contentType?.match(ContentType("application", "statuslist+cwt")) == true) {
            logger.debug { "Received binary CWT content" }
            StatusListContent.Binary(response.readRawBytes())
        } else {
            logger.debug { "Received text content" }
            StatusListContent.Text(response.bodyAsText())
        }
        remember(url, content, cacheControl)
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

    private suspend fun remember(url: String, content: StatusListContent, cacheControl: String?) {
        val maxAge = HttpCacheControl.maxAgeSeconds(cacheControl) ?: return
        mutex.withLock {
            val currentTime = now()
            cache.entries.removeAll { it.value.expiresAt <= currentTime }
            cache[url] = CachedStatusList(content, currentTime + maxAge.seconds)
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
    fun maxAgeSeconds(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val directives = header.split(',').map { it.trim().lowercase() }
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
