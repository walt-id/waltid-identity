package id.walt.trust.fetcher

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.github.oshai.kotlinlogging.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Fetches trust source documents from remote URLs.
 */
object SourceFetcher {

    private val client = HttpClient(OkHttp) {
        expectSuccess = false
    }

    data class FetchResult(
        val success: Boolean,
        val content: String? = null,
        val contentType: String? = null,
        val statusCode: Int? = null,
        val error: String? = null
    )

    suspend fun fetch(url: String): FetchResult {
        return try {
            log.info { "Fetching trust source from: $url" }
            val response = client.get(url)
            
            if (response.status.isSuccess()) {
                FetchResult(
                    success = true,
                    content = response.bodyAsText(),
                    contentType = response.contentType()?.toString(),
                    statusCode = response.status.value
                )
            } else {
                FetchResult(
                    success = false,
                    statusCode = response.status.value,
                    error = "HTTP ${response.status.value}: ${response.status.description}"
                )
            }
        } catch (e: Exception) {
            log.error(e) { "Failed to fetch trust source from $url" }
            FetchResult(
                success = false,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Detect source format from content type or content inspection.
     */
    fun detectFormat(contentType: String?, content: String): SourceFormat {
        // Check content type first
        contentType?.let {
            if ("json" in it.lowercase()) return SourceFormat.JSON
            if ("xml" in it.lowercase()) return SourceFormat.XML
        }
        
        // Fallback to content inspection
        val trimmed = content.trimStart()
        return when {
            trimmed.startsWith("{") || trimmed.startsWith("[") -> SourceFormat.JSON
            trimmed.startsWith("<?xml") || trimmed.startsWith("<") -> SourceFormat.XML
            else -> SourceFormat.UNKNOWN
        }
    }

    enum class SourceFormat { JSON, XML, UNKNOWN }
}
