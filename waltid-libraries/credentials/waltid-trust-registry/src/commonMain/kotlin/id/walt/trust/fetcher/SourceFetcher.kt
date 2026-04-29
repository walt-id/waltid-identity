package id.walt.trust.fetcher

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
