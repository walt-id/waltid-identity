package id.walt.trust.fetcher

import com.sun.net.httpserver.HttpServer
import id.walt.webdatafetching.ssrf.PrivateNetworkGuardSettings
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.UnknownHostException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The deployment's [PrivateNetworkGuardSettings] (`network-fetch-guard.conf`) relax the loopback and private-network
 * checks of [SourceFetcher], as they do for every other guarded fetch, and never the link-local, cloud metadata or
 * unspecified-address checks. Fetches only reach a local server on 127.0.0.1; other addresses are blocked before
 * any connection or are only checked at validation and DNS level.
 */
class SourceFetcherDeploymentGuardTest {

    private val config = FetcherSecurityConfig()

    private val previousLoopbackExplicit = PrivateNetworkGuardSettings.allowLoopbackExplicit
    private val previousLoopback = PrivateNetworkGuardSettings.allowLoopback
    private val previousPrivate = PrivateNetworkGuardSettings.allowPrivateNetworks
    private val previousHosts = PrivateNetworkGuardSettings.trustedHostSuffixes
    private val previousLoopbackProperty: String? = System.getProperty(TEST_LOOPBACK_PROPERTY)

    @AfterEach
    fun restore() {
        if (previousLoopbackExplicit) {
            PrivateNetworkGuardSettings.allowLoopback = previousLoopback
        } else {
            PrivateNetworkGuardSettings.clearAllowLoopbackChoice()
        }
        PrivateNetworkGuardSettings.allowPrivateNetworks = previousPrivate
        PrivateNetworkGuardSettings.trustedHostSuffixes = previousHosts
        if (previousLoopbackProperty == null) {
            System.clearProperty(TEST_LOOPBACK_PROPERTY)
        } else {
            System.setProperty(TEST_LOOPBACK_PROPERTY, previousLoopbackProperty)
        }
    }

    @Test
    fun `allowPrivateNetworks allows private addresses but never link-local or cloud metadata`() {
        PrivateNetworkGuardSettings.allowPrivateNetworks = false
        assertEquals(false to "Private/site-local addresses are blocked", SourceFetcher.validateUrl("http://10.0.0.1/", config))

        PrivateNetworkGuardSettings.allowPrivateNetworks = true
        for ((url, address) in listOf("http://10.0.0.1/" to "10.0.0.1", "http://[fd12::1]/" to "fd12::1")) {
            assertEquals(true to null, SourceFetcher.validateUrl(url, config), "validateUrl blocked $url")
            assertEquals(listOf(InetAddress.getByName(address)), SourceFetcher.SecureDns(config).lookup(address))
        }
        assertEquals(false to "Link-local addresses are blocked", SourceFetcher.validateUrl("http://169.254.169.254/", config))
        assertEquals(false to "Cloud metadata endpoints are blocked", SourceFetcher.validateUrl("http://[fd00:ec2::254]/", config))
    }

    @Test
    fun `allowLoopback opens loopback fetches and an explicit false wins over the test property`() = runBlocking {
        System.setProperty(TEST_LOOPBACK_PROPERTY, "true")
        withTrustListServer { port ->
            PrivateNetworkGuardSettings.allowLoopback = false
            val blocked = SourceFetcher.fetch("http://127.0.0.1:$port$TRUST_LIST_PATH", config)
            assertEquals("URL validation failed: Loopback addresses are blocked", blocked.error)

            PrivateNetworkGuardSettings.allowLoopback = true
            val fetched = SourceFetcher.fetch("http://127.0.0.1:$port$TRUST_LIST_PATH", config)
            assertTrue(fetched.success, "Fetch failed: ${fetched.error}")
            assertEquals(TRUST_LIST, fetched.content)
        }
        assertEquals(false to "Unspecified addresses (0.0.0.0, ::) are blocked", SourceFetcher.validateUrl("http://0.0.0.0/", config))
    }

    @Test
    fun `a trusted host suffix skips the loopback check for matching hostnames only`() = runBlocking {
        PrivateNetworkGuardSettings.allowLoopback = false
        PrivateNetworkGuardSettings.trustedHostSuffixes = setOf("localhost")
        withTrustListServer { port ->
            // Passes validateUrl, SecureDns and SecureSocketFactory by hostname.
            val fetched = SourceFetcher.fetch("http://localhost:$port$TRUST_LIST_PATH", config)
            assertTrue(fetched.success, "Fetch failed: ${fetched.error}")
            assertEquals(TRUST_LIST, fetched.content)

            val literal = SourceFetcher.fetch("http://127.0.0.1:$port$TRUST_LIST_PATH", config)
            assertEquals("URL validation failed: Loopback addresses are blocked", literal.error)
        }
    }

    @Test
    fun `opt-outs apply to IPv4 embedded in IPv6 transition addresses`() {
        // NAT64 (64:ff9b::/96) for 10.0.0.1, 127.0.0.1 and 169.254.169.254.
        PrivateNetworkGuardSettings.allowLoopback = true
        PrivateNetworkGuardSettings.allowPrivateNetworks = false
        assertBlocked("Private/site-local addresses are blocked", "http://[64:ff9b::a00:1]/")
        assertEquals(true to null, SourceFetcher.validateUrl("http://[64:ff9b::7f00:1]/", config))

        PrivateNetworkGuardSettings.allowPrivateNetworks = true
        assertEquals(true to null, SourceFetcher.validateUrl("http://[64:ff9b::a00:1]/", config))
        assertBlocked("Link-local addresses are blocked", "http://[64:ff9b::a9fe:a9fe]/")
    }

    @Test
    fun `a trusted host cannot redirect to an untrusted loopback literal`() = runBlocking {
        PrivateNetworkGuardSettings.allowLoopback = false
        PrivateNetworkGuardSettings.trustedHostSuffixes = setOf("localhost")
        withTrustListServer { port ->
            // localhost passes by name; the redirect target 127.0.0.1 is caught at SecureSocketFactory.
            val redirected = SourceFetcher.fetch("http://localhost:$port$REDIRECT_PATH", config)
            assertEquals(false, redirected.success)
            assertTrue(redirected.error.orEmpty().contains("Loopback addresses are blocked"), redirected.error)
        }
    }

    @Test
    fun `a trusted host is still refused link-local and cloud metadata addresses`() {
        PrivateNetworkGuardSettings.trustedHostSuffixes = setOf("169.254.169.254")
        assertEquals(false to "Link-local addresses are blocked", SourceFetcher.validateUrl("http://169.254.169.254/", config))
        assertFailsWith<UnknownHostException> { SourceFetcher.SecureDns(config).lookup("169.254.169.254") }
        assertFailsWith<SourceFetcher.BlockedAddressException> {
            SourceFetcher.SecureSocketFactory(config).createSocket()
                .connect(InetSocketAddress(InetAddress.getByName("169.254.169.254"), 80))
        }
    }

    private fun assertBlocked(reason: String, url: String) {
        val (allowed, actual) = SourceFetcher.validateUrl(url, config)
        assertEquals(false, allowed, "validateUrl allowed $url")
        assertTrue(actual.orEmpty().startsWith(reason), "$url: $actual")
    }

    private suspend fun <T> withTrustListServer(block: suspend (port: Int) -> T): T {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext(TRUST_LIST_PATH) { exchange ->
            val body = TRUST_LIST.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext(REDIRECT_PATH) { exchange ->
            exchange.responseHeaders.add("Location", "http://127.0.0.1:${server.address.port}$TRUST_LIST_PATH")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.start()
        return try {
            block(server.address.port)
        } finally {
            server.stop(0)
        }
    }

    private companion object {
        const val TEST_LOOPBACK_PROPERTY = "walt.ssrf.allowLoopback"
        const val TRUST_LIST_PATH = "/trust-list.json"
        const val REDIRECT_PATH = "/redirect"
        const val TRUST_LIST = """{"trustList":"deployment-guard-test"}"""
    }
}
