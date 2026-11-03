package id.walt.trust.fetcher

import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * SSRF guard tests for addresses that Java's `InetAddress` predicates do not classify as local:
 * IPv6 transition addresses (NAT64, 6to4, Teredo) that embed an IPv4 address, and the unspecified addresses.
 * Each check runs against both layers: URL pre-validation and the connect-time [SourceFetcher.SecureDns].
 * All addresses are IP literals, so resolution never leaves the JVM.
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
    }

    @Test
    fun `blocks the unspecified addresses, which reach the local host`() {
        val reason = "Unspecified addresses (0.0.0.0, ::) are blocked"
        assertEquals(false to reason, SourceFetcher.validateUrl("http://0.0.0.0/", config))
        assertEquals(false to reason, SourceFetcher.validateUrl("http://[::]/", config))
        assertFailsWith<UnknownHostException> { SourceFetcher.SecureDns(config).lookup("0.0.0.0") }
        assertFailsWith<UnknownHostException> { SourceFetcher.SecureDns(config).lookup("::") }
    }

    @Test
    fun `blocks the IPv6 cloud metadata address when private IP blocking is off`() {
        val linkLocalOnly = FetcherSecurityConfig(blockPrivateIPs = false)
        assertEquals(false to "Cloud metadata endpoints are blocked", SourceFetcher.validateUrl("http://[fd00:ec2::254]/", linkLocalOnly))
        assertFailsWith<UnknownHostException> { SourceFetcher.SecureDns(linkLocalOnly).lookup("fd00:ec2::254") }
    }
}
