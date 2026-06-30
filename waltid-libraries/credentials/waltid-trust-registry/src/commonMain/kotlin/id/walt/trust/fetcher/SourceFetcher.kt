package id.walt.trust.fetcher

import io.ktor.http.Url

/**
 * Configuration for URL validation to prevent SSRF attacks.
 */
data class FetcherSecurityConfig(
    /**
     * If non-empty, only URLs with hostnames in this list are allowed.
     * Use this for strict allow-listing of trusted trust list providers.
     */
    val allowedHosts: Set<String> = emptySet(),

    /**
     * Block requests to private/internal IP ranges (10.x, 172.16-31.x, 192.168.x, 127.x, etc.)
     * Enabled by default to prevent SSRF attacks.
     */
    val blockPrivateIPs: Boolean = true,

    /**
     * Block requests to link-local addresses (169.254.x.x) including cloud metadata endpoints.
     * Enabled by default to prevent SSRF attacks against cloud metadata services.
     */
    val blockLinkLocal: Boolean = true,

    /**
     * Block requests to loopback addresses (127.x.x.x, ::1).
     * Enabled by default to prevent SSRF attacks against localhost services.
     */
    val blockLoopback: Boolean = true,

    /**
     * Allowed URL schemes. Defaults to HTTPS and HTTP for trust lists.
     */
    val allowedSchemes: Set<String> = setOf("https", "http")
)

data class FetchResult(
    val success: Boolean,
    val content: String? = null,
    val contentType: String? = null,
    val statusCode: Int? = null,
    val error: String? = null
)

enum class SourceFormat { JSON, XML, UNKNOWN }

internal data class BasicValidatedUrl(
    val parsedUrl: Url,
    val normalizedHost: String,
)

internal object SourceFetcherCommon {
    fun detectSourceFormat(contentType: String?, content: String): SourceFormat {
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

    fun validateUrlBasic(
        url: String,
        config: FetcherSecurityConfig,
    ): Pair<BasicValidatedUrl?, String?> {
        val parsedUrl = try {
            Url(url)
        } catch (e: Exception) {
            return null to "Invalid URL format: ${e.message}"
        }

        val scheme = parsedUrl.protocol.name.lowercase()
        if (scheme !in config.allowedSchemes) {
            return null to "Scheme '$scheme' not allowed. Allowed: ${config.allowedSchemes}"
        }

        val host = parsedUrl.host.lowercase()
        if (host.isBlank()) {
            return null to "No host specified in URL"
        }

        val allowedHosts = config.allowedHosts.map { it.lowercase() }.toSet()
        if (allowedHosts.isNotEmpty() && host !in allowedHosts) {
            return null to "Host '$host' not in allowed hosts list"
        }

        return BasicValidatedUrl(parsedUrl = parsedUrl, normalizedHost = host) to null
    }
}

/**
 * Fetches trust source documents from remote URLs with SSRF protection.
 *
 * The JVM implementation uses OkHttp with a custom DNS resolver for DNS-pinning
 * to prevent DNS rebinding attacks. Other platforms should provide their own
 * platform-appropriate implementation.
 */
expect object SourceFetcher {
    var securityConfig: FetcherSecurityConfig

    suspend fun fetch(url: String, config: FetcherSecurityConfig = securityConfig): FetchResult

    fun detectFormat(contentType: String?, content: String): SourceFormat
}
