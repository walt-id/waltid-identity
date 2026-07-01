package id.walt.trust.fetcher

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import okhttp3.Dns
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

private val log = KotlinLogging.logger {}

/**
 * JVM implementation of [SourceFetcher] using OkHttp.
 *
 * Security measures:
 * - URL scheme validation (https/http only)
 * - Hostname allow-listing (optional)
 * - Private IP blocking at DNS resolution time via [SecureDns]
 * - DNS pinning to prevent TOCTOU/rebinding attacks
 * - Request timeouts to prevent resource exhaustion
 */
actual object SourceFetcher {

    actual var securityConfig: FetcherSecurityConfig = FetcherSecurityConfig()

    /**
     * Custom DNS resolver that validates resolved IPs against security policy.
     * This prevents DNS rebinding attacks by enforcing the same IP validation
     * at actual connection time, not just at preflight check time.
     */
    private class SecureDns(private val config: FetcherSecurityConfig) : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val addresses = Dns.SYSTEM.lookup(hostname)

            for (addr in addresses) {
                if (config.blockLoopback && addr.isLoopbackAddress) {
                    throw UnknownHostException("Loopback addresses are blocked: $hostname")
                }
                if (config.blockLinkLocal && addr.isLinkLocalAddress) {
                    throw UnknownHostException("Link-local addresses are blocked: $hostname")
                }
                if (config.blockPrivateIPs) {
                    if (addr.isSiteLocalAddress) {
                        throw UnknownHostException("Private/site-local addresses are blocked: $hostname")
                    }
                    if (addr is Inet6Address) {
                        val bytes = addr.address
                        val firstByte = bytes[0].toInt() and 0xFF
                        if (firstByte == 0xFC || firstByte == 0xFD) {
                            throw UnknownHostException("IPv6 Unique Local Addresses (ULA) are blocked: $hostname")
                        }
                    }
                }
                if (config.blockLinkLocal) {
                    val addrStr = addr.hostAddress
                    if (addrStr == "169.254.169.254" || addrStr == "fd00:ec2::254") {
                        throw UnknownHostException("Cloud metadata endpoints are blocked: $hostname")
                    }
                }
            }

            return addresses
        }
    }

    private fun createClient(config: FetcherSecurityConfig): HttpClient {
        return HttpClient(OkHttp) {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
            engine {
                config {
                    dns(SecureDns(config))
                }
            }
        }
    }

    actual suspend fun fetch(url: String, config: FetcherSecurityConfig): FetchResult {
        val validationResult = validateUrl(url, config)
        if (!validationResult.first) {
            log.warn { "URL validation failed for $url: ${validationResult.second}" }
            return FetchResult(
                success = false,
                error = "URL validation failed: ${validationResult.second}"
            )
        }

        val client = createClient(config)

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
        } catch (e: UnknownHostException) {
            log.warn { "SSRF protection blocked request to $url: ${e.message}" }
            FetchResult(success = false, error = "Blocked by security policy: ${e.message}")
        } catch (e: Exception) {
            log.error(e) { "Failed to fetch trust source from $url" }
            FetchResult(success = false, error = e.message ?: "Unknown error")
        } finally {
            client.close()
        }
    }

    actual fun detectFormat(contentType: String?, content: String): SourceFormat {
        return SourceFetcherCommon.detectSourceFormat(contentType, content)
    }

    /**
     * Validates a URL for SSRF protection (JVM-only, uses Java's InetAddress).
     */
    internal fun validateUrl(url: String, config: FetcherSecurityConfig = securityConfig): Pair<Boolean, String?> {
        val (validatedUrl, validationError) = SourceFetcherCommon.validateUrlBasic(url, config)
        if (validatedUrl == null) {
            return false to validationError
        }

        val host = validatedUrl.normalizedHost

        if (config.allowedHosts.isNotEmpty()) {
            return true to null
        }

        val addresses = try {
            InetAddress.getAllByName(host)
        } catch (e: Exception) {
            return false to "Failed to resolve hostname: ${e.message}"
        }

        for (addr in addresses) {
            if (config.blockLoopback && addr.isLoopbackAddress) {
                return false to "Loopback addresses are blocked"
            }
            if (config.blockLinkLocal && addr.isLinkLocalAddress) {
                return false to "Link-local addresses are blocked"
            }
            if (config.blockPrivateIPs) {
                if (addr.isSiteLocalAddress) {
                    return false to "Private/site-local addresses are blocked"
                }
                if (addr is Inet6Address && isIPv6UniqueLocalAddress(addr)) {
                    return false to "IPv6 Unique Local Addresses (ULA) are blocked"
                }
            }
            if (config.blockLinkLocal) {
                val addrStr = addr.hostAddress
                if (addrStr == "169.254.169.254" || addrStr == "fd00:ec2::254") {
                    return false to "Cloud metadata endpoints are blocked"
                }
            }
        }

        return true to null
    }

    private fun isIPv6UniqueLocalAddress(addr: Inet6Address): Boolean {
        val bytes = addr.address
        val firstByte = bytes[0].toInt() and 0xFF
        return firstByte == 0xFC || firstByte == 0xFD
    }
}
