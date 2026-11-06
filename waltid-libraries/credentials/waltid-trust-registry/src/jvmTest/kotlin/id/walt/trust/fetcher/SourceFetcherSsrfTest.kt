package id.walt.trust.fetcher

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.UnknownHostException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * SSRF guard tests for addresses that Java's `InetAddress` predicates do not classify as local:
 * IPv6 transition addresses (NAT64, 6to4, Teredo) that embed an IPv4 address, and the unspecified addresses.
 * Checks run against URL pre-validation and [SourceFetcher.SecureDns], which stands in for a hostname that
 * resolves to the address (OkHttp only calls it for hostnames). Blocked addresses also run against
 * [SourceFetcher.SecureSocketFactory], which guards every connect, including IP literals and redirect targets.
 * All addresses are IP literals, so nothing leaves the JVM.
 */
class SourceFetcherSsrfTest {

    private val config = FetcherSecurityConfig()

    private fun assertBlocked(address: String) {
        val (allowed, reason) = SourceFetcher.validateUrl("http://[$address]/", config)
        assertFalse(allowed, "validateUrl allowed $address")
        assertEquals(true, reason?.contains("embedded in IPv6 transition address"), "Unexpected reason for $address: $reason")
        assertFailsWith<UnknownHostException>("SecureDns resolved $address") {
            SourceFetcher.SecureDns(config).lookup(address)
        }
        assertSocketBlocked(address)
    }

    private fun assertSocketBlocked(address: String) {
        assertFailsWith<SourceFetcher.BlockedAddressException>("Socket connected to $address") {
            SourceFetcher.SecureSocketFactory(config).createSocket().connect(InetSocketAddress(InetAddress.getByName(address), 80))
        }
    }

    private fun assertAllowed(address: String) {
        assertEquals(true to null, SourceFetcher.validateUrl("http://[$address]/", config), "validateUrl blocked $address")
        assertEquals(listOf(InetAddress.getByName(address)), SourceFetcher.SecureDns(config).lookup(address))
    }

    @Test
    fun `blocks NAT64 addresses embedding a loopback, private or cloud metadata IPv4 address`() {
        assertBlocked("64:ff9b::7f00:1") // 127.0.0.1
        assertBlocked("64:ff9b::a00:1") // 10.0.0.1
        assertBlocked("64:ff9b::a9fe:a9fe") // 169.254.169.254
    }

    @Test
    fun `blocks 6to4 addresses embedding a loopback, private or cloud metadata IPv4 address`() {
        assertBlocked("2002:7f00:1::") // 127.0.0.1
        assertBlocked("2002:c0a8:101::") // 192.168.1.1
        assertBlocked("2002:a9fe:a9fe::") // 169.254.169.254
    }

    @Test
    fun `blocks Teredo addresses whose bit-inverted client address is loopback, private or cloud metadata`() {
        // Server 65.54.227.120, flags and obfuscated port, then the client IPv4 address xor 0xFF.
        assertBlocked("2001:0:4136:e378:8000:63bf:80ff:fffe") // 127.0.0.1
        assertBlocked("2001:0:4136:e378:8000:63bf:f5ff:fffe") // 10.0.0.1
        assertBlocked("2001:0:4136:e378:8000:63bf:5601:5601") // 169.254.169.254
    }

    @Test
    fun `allows transition addresses embedding a public IPv4 address`() {
        assertAllowed("64:ff9b::808:808") // NAT64 8.8.8.8, as DNS64 returns in IPv6-only networks
        assertAllowed("2002:808:808::") // 6to4 8.8.8.8
        assertAllowed("2001:0:4136:e378:8000:63bf:f7f7:f7f7") // Teredo client 8.8.8.8
    }

    @Test
    fun `applies the configured block rules to the embedded IPv4 address`() {
        val loopbackAllowed = FetcherSecurityConfig(blockLoopback = false)
        assertEquals(true to null, SourceFetcher.validateUrl("http://[64:ff9b::7f00:1]/", loopbackAllowed))
        assertEquals(listOf(InetAddress.getByName("64:ff9b::7f00:1")), SourceFetcher.SecureDns(loopbackAllowed).lookup("64:ff9b::7f00:1"))
    }

    @Test
    fun `blocks the unspecified addresses, which reach the local host`() {
        val reason = "Unspecified addresses (0.0.0.0, ::) are blocked"
        assertEquals(false to reason, SourceFetcher.validateUrl("http://0.0.0.0/", config))
        assertEquals(false to reason, SourceFetcher.validateUrl("http://[::]/", config))
        assertFailsWith<UnknownHostException> { SourceFetcher.SecureDns(config).lookup("0.0.0.0") }
        assertFailsWith<UnknownHostException> { SourceFetcher.SecureDns(config).lookup("::") }
        assertSocketBlocked("0.0.0.0")
        assertSocketBlocked("::")
    }

    @Test
    fun `fetch refuses a redirect to a blocked IP literal`() = runBlocking {
        val redirects = mapOf(
            "/metadata" to "http://169.254.169.254/latest/meta-data/",
            "/nat64-metadata" to "http://[64:ff9b::a9fe:a9fe]/latest/meta-data/",
        )
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        redirects.forEach { (path, location) ->
            server.createContext(path) { exchange ->
                exchange.responseHeaders.add("Location", location)
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
        }
        server.start()
        try {
            // Loopback is allowed so the first hop reaches the local server; the redirect target must still be refused.
            val loopbackAllowed = FetcherSecurityConfig(blockLoopback = false)
            for (path in redirects.keys) {
                val result = SourceFetcher.fetch("http://127.0.0.1:${server.address.port}$path", loopbackAllowed)
                assertFalse(result.success, "Redirect from $path was followed")
                assertEquals(
                    true,
                    result.error?.startsWith("Blocked by security policy: Link-local addresses are blocked"),
                    "Unexpected error for $path: ${result.error}"
                )
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `blocks the IPv6 cloud metadata address when private IP blocking is off`() {
        val linkLocalOnly = FetcherSecurityConfig(blockPrivateIPs = false)
        assertEquals(false to "Cloud metadata endpoints are blocked", SourceFetcher.validateUrl("http://[fd00:ec2::254]/", linkLocalOnly))
        assertFailsWith<UnknownHostException> { SourceFetcher.SecureDns(linkLocalOnly).lookup("fd00:ec2::254") }
    }
}
