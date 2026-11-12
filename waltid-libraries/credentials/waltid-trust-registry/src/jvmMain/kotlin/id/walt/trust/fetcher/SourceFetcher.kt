package id.walt.trust.fetcher

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import okhttp3.Dns
import java.io.IOException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.UnknownHostException
import javax.net.SocketFactory

private val log = KotlinLogging.logger {}

/**
 * JVM implementation of [SourceFetcher] using OkHttp.
 *
 * Security measures:
 * - URL scheme validation (https/http only)
 * - Hostname allow-listing (optional)
 * - Private IP blocking at DNS resolution time via [SecureDns]
 * - The same blocking for IPv4 addresses embedded in NAT64, 6to4 and Teredo IPv6 addresses
 * - The same blocking on every socket connect via [SecureSocketFactory], which also covers
 *   IP-literal URLs and redirect targets that never pass through [SecureDns]
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
    internal class SecureDns(private val config: FetcherSecurityConfig) : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val addresses = Dns.SYSTEM.lookup(hostname)

            for (addr in addresses) {
                blockedReason(addr, config)?.let { throw UnknownHostException("$it: $hostname") }
            }

            return addresses
        }
    }

    internal class BlockedAddressException(message: String) : IOException(message)

    /**
     * Socket factory that validates every connection target against security policy.
     * OkHttp skips [Dns] for IP-literal hosts, and Ktor follows redirects without re-running
     * [validateUrl], so a redirect to an IP literal is only caught here.
     */
    internal class SecureSocketFactory(private val config: FetcherSecurityConfig) : SocketFactory() {
        override fun createSocket(): Socket = object : Socket() {
            override fun connect(endpoint: SocketAddress, timeout: Int) {
                val address = (endpoint as? InetSocketAddress)?.address
                if (address != null) {
                    blockedReason(address, config)?.let {
                        close()
                        throw BlockedAddressException("$it: ${address.hostAddress}")
                    }
                }
                super.connect(endpoint, timeout)
            }
        }

        override fun createSocket(host: String, port: Int): Socket =
            createSocket().apply { connect(InetSocketAddress(host, port)) }

        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
            createSocket().apply {
                bind(InetSocketAddress(localHost, localPort))
                connect(InetSocketAddress(host, port))
            }

        override fun createSocket(host: InetAddress, port: Int): Socket =
            createSocket().apply { connect(InetSocketAddress(host, port)) }

        override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
            createSocket().apply {
                bind(InetSocketAddress(localAddress, localPort))
                connect(InetSocketAddress(address, port))
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
                    socketFactory(SecureSocketFactory(config))
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
        } catch (e: BlockedAddressException) {
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
            blockedReason(addr, config)?.let { return false to it }
        }

        return true to null
    }

    // Compared as addresses, not strings: Java prints IPv6 uncompressed ("fd00:ec2:0:0:0:0:0:254").
    private val cloudMetadataAddresses = setOf(
        InetAddress.getByName("169.254.169.254"),
        InetAddress.getByName("fd00:ec2::254"),
    )

    private val nat64Prefix = byteArrayOf(0x00, 0x64, 0xFF.toByte(), 0x9B.toByte(), 0, 0, 0, 0, 0, 0, 0, 0) // 64:ff9b::/96
    private val sixToFourPrefix = byteArrayOf(0x20, 0x02) // 2002::/16
    private val teredoPrefix = byteArrayOf(0x20, 0x01, 0x00, 0x00) // 2001::/32

    /**
     * Returns why [addr] is blocked under [config], or null if it is allowed. Shared by
     * [validateUrl] and [SecureDns] so that both layers apply the same rules.
     */
    private fun blockedReason(addr: InetAddress, config: FetcherSecurityConfig): String? {
        if (config.blockLoopback && addr.isLoopbackAddress) return "Loopback addresses are blocked"
        // Connecting to 0.0.0.0 or :: reaches the local host on Linux.
        if (config.blockLoopback && addr.isAnyLocalAddress) return "Unspecified addresses (0.0.0.0, ::) are blocked"
        if (config.blockLinkLocal && addr.isLinkLocalAddress) return "Link-local addresses are blocked"
        if (config.blockPrivateIPs) {
            if (addr.isSiteLocalAddress) return "Private/site-local addresses are blocked"
            if (addr is Inet6Address && isIPv6UniqueLocalAddress(addr)) return "IPv6 Unique Local Addresses (ULA) are blocked"
        }
        if (config.blockLinkLocal && addr in cloudMetadataAddresses) return "Cloud metadata endpoints are blocked"

        val embedded = (addr as? Inet6Address)?.let(::embeddedIPv4) ?: return null
        return blockedReason(embedded, config)?.let { "$it (embedded in IPv6 transition address ${addr.hostAddress})" }
    }

    /**
     * Returns the IPv4 address that traffic to an IPv6 transition address is translated or
     * tunnelled to, or null if [addr] is none: NAT64 well-known prefix (RFC 6052), 6to4
     * (RFC 3056), or Teredo (RFC 4380), whose client address is stored bit-inverted.
     */
    private fun embeddedIPv4(addr: Inet6Address): InetAddress? {
        val bytes = addr.address
        val ipv4 = when {
            bytes.startsWith(nat64Prefix) -> bytes.copyOfRange(12, 16)
            bytes.startsWith(sixToFourPrefix) -> bytes.copyOfRange(2, 6)
            bytes.startsWith(teredoPrefix) -> ByteArray(4) { (bytes[12 + it].toInt() xor 0xFF).toByte() }
            else -> return null
        }
        return InetAddress.getByAddress(ipv4)
    }

    private fun ByteArray.startsWith(prefix: ByteArray) = prefix.indices.all { this[it] == prefix[it] }

    private fun isIPv6UniqueLocalAddress(addr: Inet6Address): Boolean {
        val bytes = addr.address
        val firstByte = bytes[0].toInt() and 0xFF
        return firstByte == 0xFC || firstByte == 0xFD
    }
}
