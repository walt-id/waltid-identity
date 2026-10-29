package id.walt.webdatafetching.ssrf

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.isSuccess
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Uses literal IP addresses (never a hostname) throughout, so every case - blocked and allowed alike - is
 * resolved by [java.net.InetAddress] without an actual DNS lookup or network call, keeping the test hermetic.
 * 8.8.8.8 / 8.8.4.4 stand in for "some public address" purely because they parse as valid, non-private IPv4
 * literals; nothing here actually talks to Google.
 */
class PrivateNetworkGuardTest {

    // PrivateNetworkGuardSettings is global mutable state, so every test must leave it as it found it.
    @BeforeTest
    @AfterTest
    fun resetSettings() {
        System.clearProperty("walt.ssrf.allowLoopback")
        PrivateNetworkGuardSettings.allowLoopback = false
        PrivateNetworkGuardSettings.allowPrivateNetworks = false
        PrivateNetworkGuardSettings.trustedHostSuffixes = emptySet()
    }

    private fun guardedClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        installPrivateNetworkGuard()
    }

    @Test
    fun `refuses a direct request to loopback`() = runTest {
        val engine = MockEngine { respondOk() }
        val client = guardedClient(engine)
        assertFailsWith<BlockedAddressException> { client.get("http://127.0.0.1/admin") }
    }

    @Test
    fun `refuses a direct request to the cloud metadata address`() = runTest {
        val engine = MockEngine { respondOk() }
        val client = guardedClient(engine)
        assertFailsWith<BlockedAddressException> { client.get("http://169.254.169.254/latest/meta-data/") }
    }

    @Test
    fun `refuses a direct request to a private RFC1918 address`() = runTest {
        val engine = MockEngine { respondOk() }
        val client = guardedClient(engine)
        assertFailsWith<BlockedAddressException> { client.get("http://10.0.0.5/") }
    }

    @Test
    fun `refuses a non-http scheme`() = runTest {
        val engine = MockEngine { respondOk() }
        val client = guardedClient(engine)
        assertFailsWith<IllegalArgumentException> { client.get("file:///etc/passwd") }
    }

    @Test
    fun `allows a direct request to a public-looking address`() = runTest {
        val engine = MockEngine { respondOk("hello") }
        val client = guardedClient(engine)
        val response = client.get("http://8.8.8.8/")
        assertTrue(response.status.isSuccess())
    }

    @Test
    fun `refuses a redirect that points at a private address, without ever requesting it`() = runTest {
        var requestedLoopback = false
        val engine = MockEngine { request ->
            if (request.url.host == "127.0.0.1") {
                requestedLoopback = true
                respondOk("should never get here")
            } else {
                respond(
                    content = "",
                    status = HttpStatusCode.Found,
                    headers = headersOf(HttpHeaders.Location, "http://127.0.0.1/internal-secret"),
                )
            }
        }
        val client = guardedClient(engine)

        assertFailsWith<BlockedAddressException> { client.get("http://8.8.8.8/redirect-me") }
        assertTrue(!requestedLoopback, "the guard must reject the redirect target before it is ever requested")
    }

    @Test
    fun `an unset loopback choice follows the test system property`() = runTest {
        PrivateNetworkGuardSettings.clearAllowLoopbackChoice()
        System.setProperty("walt.ssrf.allowLoopback", "true")
        val engine = MockEngine { respondOk("hello") }
        val client = guardedClient(engine)
        assertTrue(client.get("http://127.0.0.1/admin").status.isSuccess())
        assertTrue(!PrivateNetworkGuardSettings.allowLoopbackExplicit)
    }

    @Test
    fun `an explicit loopback deny wins over the test system property`() = runTest {
        System.setProperty("walt.ssrf.allowLoopback", "true")
        PrivateNetworkGuardSettings.allowLoopback = false
        val engine = MockEngine { respondOk() }
        val client = guardedClient(engine)
        assertFailsWith<BlockedAddressException> { client.get("http://127.0.0.1/admin") }
    }

    @Test
    fun `ipv4-mapped loopback and private addresses are refused`() = runTest {
        val engine = MockEngine { respondOk() }
        val client = guardedClient(engine)
        assertFailsWith<BlockedAddressException> { client.get("http://[::ffff:127.0.0.1]/admin") }
        assertFailsWith<BlockedAddressException> { client.get("http://[::ffff:10.1.2.3]/") }
    }

    @Test
    fun `okhttp connect dns refuses loopback and returns a public literal`() {
        assertFailsWith<java.net.UnknownHostException> { ConnectTimeDns.lookup("127.0.0.1") }
        assertTrue(ConnectTimeDns.lookup("8.8.8.8").isNotEmpty())
    }

    @Test
    fun `allows loopback once allowLoopback is set`() = runTest {
        PrivateNetworkGuardSettings.allowLoopback = true
        val engine = MockEngine { respondOk("hello") }
        val client = guardedClient(engine)
        assertTrue(client.get("http://127.0.0.1/admin").status.isSuccess())
    }

    @Test
    fun `allows a private RFC1918 address once allowPrivateNetworks is set`() = runTest {
        PrivateNetworkGuardSettings.allowPrivateNetworks = true
        val engine = MockEngine { respondOk("hello") }
        val client = guardedClient(engine)
        assertTrue(client.get("http://10.0.0.5/").status.isSuccess())
    }

    @Test
    fun `still refuses the cloud metadata address even with both settings enabled`() = runTest {
        PrivateNetworkGuardSettings.allowLoopback = true
        PrivateNetworkGuardSettings.allowPrivateNetworks = true
        val engine = MockEngine { respondOk() }
        val client = guardedClient(engine)
        assertFailsWith<BlockedAddressException> { client.get("http://169.254.169.254/latest/meta-data/") }
    }

    @Test
    fun `allowLoopback does not also allow a private RFC1918 address`() = runTest {
        PrivateNetworkGuardSettings.allowLoopback = true
        val engine = MockEngine { respondOk() }
        val client = guardedClient(engine)
        assertFailsWith<BlockedAddressException> { client.get("http://10.0.0.5/") }
    }

    @Test
    fun `trustedHostSuffixes does not affect an untrusted loopback hostname`() = runTest {
        val engine = MockEngine { respondOk() }
        val client = guardedClient(engine)
        assertFailsWith<BlockedAddressException> { client.get("http://localhost/admin") }
    }

    @Test
    fun `allows a loopback-resolving hostname that exactly matches a trusted suffix`() = runTest {
        PrivateNetworkGuardSettings.trustedHostSuffixes = setOf("localhost")
        val engine = MockEngine { respondOk("hello") }
        val client = guardedClient(engine)
        assertTrue(client.get("http://localhost/admin").status.isSuccess())
    }

    @Test
    fun `allows a loopback-resolving hostname that is a subdomain of a trusted suffix`() = runTest {
        // Mirrors the real scenario: org1.enterprise.waltid.cloud is a subdomain of the deployment's own
        // base domain, and split-horizon or NAT-hairpin DNS can resolve it to a loopback/private address.
        PrivateNetworkGuardSettings.trustedHostSuffixes = setOf("localhost")
        val engine = MockEngine { respondOk("hello") }
        val client = guardedClient(engine)
        assertTrue(client.get("http://org1.localhost/admin").status.isSuccess())
    }

    @Test
    fun `a trusted host still cannot reach the cloud metadata address`() = runTest {
        // The always-blocked categories (link-local/multicast/wildcard) are checked before host trust, so
        // trusting a suffix can never be used to reach one - even if DNS somehow pointed a trusted hostname
        // there (compromised record, cache poisoning, ...).
        PrivateNetworkGuardSettings.trustedHostSuffixes = setOf("trusted.example")
        val engine = MockEngine { respondOk() }
        val client = guardedClient(engine)
        assertFailsWith<BlockedAddressException> { client.get("http://169.254.169.254/latest/meta-data/") }
    }

    @Test
    fun `follows a relative redirect refusal as unsupported, not as a blocked address`() = runTest {
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/redirect-me") {
                respond(
                    content = "",
                    status = HttpStatusCode.Found,
                    headers = headersOf(HttpHeaders.Location, "/relative-path"),
                )
            } else {
                error("unexpected request: ${request.url}")
            }
        }
        val client = guardedClient(engine)
        assertFailsWith<UnsupportedRedirectException> { client.get("http://8.8.8.8/redirect-me") }
    }

    @Test
    fun `follows a redirect chain that stays on allowed addresses`() = runTest {
        val engine = MockEngine { request ->
            when (request.url.host) {
                "8.8.8.8" -> respond(
                    content = "",
                    status = HttpStatusCode.Found,
                    headers = headersOf(HttpHeaders.Location, "http://8.8.4.4/final"),
                )
                "8.8.4.4" -> respondOk("final destination")
                else -> error("unexpected host: ${request.url.host}")
            }
        }
        val client = guardedClient(engine)

        val response = client.get("http://8.8.8.8/redirect-me")
        assertTrue(response.status.isSuccess())
    }

    @Test
    fun `a client that disables redirects gets the redirect and does not request the target`() = runTest {
        var requests = 0
        val engine = MockEngine {
            requests++
            respond(
                content = "",
                status = HttpStatusCode.TemporaryRedirect,
                headers = headersOf(HttpHeaders.Location, "http://8.8.4.4/stolen"),
            )
        }
        val client = HttpClient(engine) {
            followRedirects = false
            installPrivateNetworkGuard()
        }
        val response = client.post("http://8.8.8.8/hook") { setBody("payload") }
        assertEquals(HttpStatusCode.TemporaryRedirect, response.status)
        assertEquals(1, requests)
    }

    @Test
    fun `a POST is not followed even when the client follows redirects`() = runTest {
        var requests = 0
        val engine = MockEngine {
            requests++
            respond(
                content = "",
                status = HttpStatusCode.Found,
                headers = headersOf(HttpHeaders.Location, "http://8.8.4.4/next"),
            )
        }
        val client = guardedClient(engine)
        val response = client.post("http://8.8.8.8/hook") { setBody("payload") }
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals(1, requests)
    }

    @Test
    fun `a cross-authority redirect drops Authorization`() = runTest {
        val authorizations = mutableListOf<String?>()
        val engine = MockEngine { request ->
            authorizations += request.headers[HttpHeaders.Authorization]
            if (request.url.host == "8.8.8.8") {
                respond(
                    content = "",
                    status = HttpStatusCode.Found,
                    headers = headersOf(HttpHeaders.Location, "http://1.1.1.1/next"),
                )
            } else {
                respondOk("ok")
            }
        }
        val client = guardedClient(engine)
        client.get("http://8.8.8.8/start") {
            header(HttpHeaders.Authorization, "Bearer secret")
        }
        assertEquals(listOf("Bearer secret", null), authorizations)
    }

    @Test
    fun `an unknown host is a DNS failure and not a blocked address`() = runTest {
        val engine = MockEngine { respondOk() }
        val client = guardedClient(engine)
        val error = runCatching { client.get("http://no-such-host.invalid/") }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(generateSequence(error) { it.cause }.none { it is BlockedAddressException })
    }
}
