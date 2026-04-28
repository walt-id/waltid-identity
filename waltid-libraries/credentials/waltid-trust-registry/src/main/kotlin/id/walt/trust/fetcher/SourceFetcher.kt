package id.walt.trust.fetcher

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.InetAddress
import java.net.URI

private val log = KotlinLogging.logger {}

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
     * Allowed URL schemes. Defaults to HTTPS only for trust lists.
     * Add "http" only if you need to support legacy trust list providers.
     */
    val allowedSchemes: Set<String> = setOf("https", "http")
)

/**
 * Fetches trust source documents from remote URLs with SSRF protection.
 */
object SourceFetcher {

    private val client = HttpClient(OkHttp) {
        expectSuccess = false
    }
    
    /**
     * Default security configuration - restrictive by default.
     * Override with a custom config for specific deployment needs.
     */
    var securityConfig = FetcherSecurityConfig()

    data class FetchResult(
        val success: Boolean,
        val content: String? = null,
        val contentType: String? = null,
        val statusCode: Int? = null,
        val error: String? = null
    )

    suspend fun fetch(url: String, config: FetcherSecurityConfig = securityConfig): FetchResult {
        // Validate URL before fetching
        val validationResult = validateUrl(url, config)
        if (!validationResult.first) {
            log.warn { "URL validation failed for $url: ${validationResult.second}" }
            return FetchResult(
                success = false,
                error = "URL validation failed: ${validationResult.second}"
            )
        }
        
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
     * Validates a URL for SSRF protection.
     * 
     * @return Pair of (isValid, errorMessage)
     */
    internal fun validateUrl(url: String, config: FetcherSecurityConfig = securityConfig): Pair<Boolean, String?> {
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            return false to "Invalid URL format: ${e.message}"
        }
        
        // Check scheme
        val scheme = uri.scheme?.lowercase()
        if (scheme == null || scheme !in config.allowedSchemes) {
            return false to "Scheme '$scheme' not allowed. Allowed: ${config.allowedSchemes}"
        }
        
        // Check host
        val host = uri.host
        if (host.isNullOrBlank()) {
            return false to "No host specified in URL"
        }
        
        // If allowlist is configured, check it
        if (config.allowedHosts.isNotEmpty()) {
            if (host.lowercase() !in config.allowedHosts.map { it.lowercase() }) {
                return false to "Host '$host' not in allowed hosts list"
            }
            // If host is explicitly allowed, skip IP validation
            return true to null
        }
        
        // Resolve hostname to IP for validation
        val addresses = try {
            InetAddress.getAllByName(host)
        } catch (e: Exception) {
            return false to "Failed to resolve hostname: ${e.message}"
        }
        
        for (addr in addresses) {
            // Check for loopback
            if (config.blockLoopback && addr.isLoopbackAddress) {
                return false to "Loopback addresses are blocked"
            }
            
            // Check for link-local (169.254.x.x for IPv4, fe80:: for IPv6)
            if (config.blockLinkLocal && addr.isLinkLocalAddress) {
                return false to "Link-local addresses are blocked"
            }
            
            // Check for private/site-local addresses
            if (config.blockPrivateIPs && addr.isSiteLocalAddress) {
                return false to "Private/site-local addresses are blocked"
            }
            
            // Additional check for cloud metadata endpoint (169.254.169.254)
            if (config.blockLinkLocal) {
                val addrStr = addr.hostAddress
                if (addrStr == "169.254.169.254" || addrStr == "fd00:ec2::254") {
                    return false to "Cloud metadata endpoints are blocked"
                }
            }
        }
        
        return true to null
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
