package id.walt.trust.fetcher

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * JS implementation of [SourceFetcher] using the default Ktor JS engine.
 *
 * Note: SSRF protection (IP blocking, DNS pinning) is not implemented on JS, as the platform's
 * security model (same-origin policy in browsers, OS-level controls in Node.js) provides
 * equivalent protections in typical deployment scenarios.
 * For server-side JS environments where SSRF is a concern, use the remote service mode
 * of [id.walt.policies2.vc.policies.ETSITrustListPolicy] with an explicitly trusted URL instead.
 */
actual object SourceFetcher {

    actual var securityConfig: FetcherSecurityConfig = FetcherSecurityConfig()

    actual suspend fun fetch(url: String, config: FetcherSecurityConfig): FetchResult {
        val scheme = url.substringBefore("://").lowercase()
        if (scheme !in config.allowedSchemes) {
            return FetchResult(success = false, error = "Scheme '$scheme' not allowed. Allowed: ${config.allowedSchemes}")
        }

        return try {
            HttpClient().use { client ->
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
            }
        } catch (e: Exception) {
            FetchResult(success = false, error = e.message ?: "Unknown error")
        }
    }

    actual fun detectFormat(contentType: String?, content: String): SourceFormat {
        contentType?.let {
            if ("json" in it.lowercase()) return SourceFormat.JSON
            if ("xml" in it.lowercase()) return SourceFormat.XML
        }
        val trimmed = content.trimStart()
        return when {
            trimmed.startsWith("{") || trimmed.startsWith("[") -> SourceFormat.JSON
            trimmed.startsWith("<?xml") || trimmed.startsWith("<") -> SourceFormat.XML
            else -> SourceFormat.UNKNOWN
        }
    }
}
