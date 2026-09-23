package id.walt.webdatafetching.ssrf

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.isSuccess
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
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
        PrivateNetworkGuardSettings.allowLoopback = false
        PrivateNetworkGuardSettings.allowPrivateNetworks = false
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
}
