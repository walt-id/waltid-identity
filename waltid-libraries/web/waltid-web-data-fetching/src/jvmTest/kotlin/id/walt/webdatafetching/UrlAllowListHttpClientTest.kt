package id.walt.webdatafetching

import com.sun.net.httpserver.HttpServer
import id.walt.webdatafetching.config.AllowList
import id.walt.webdatafetching.config.RequestEngineConfiguration
import id.walt.webdatafetching.config.url.UrlConfiguration
import id.walt.webdatafetching.ssrf.PrivateNetworkGuardSettings
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.test.runTest
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The configured URL allow-list must apply to callers that use [WebDataFetcher.httpClient] directly. */
class UrlAllowListHttpClientTest {
    private lateinit var server: HttpServer
    private val okHits = AtomicInteger()
    private val targetHits = AtomicInteger()
    private val port get() = server.address.port

    @BeforeTest
    fun start() {
        PrivateNetworkGuardSettings.allowLoopback = true
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
            createContext("/ok") { exchange ->
                okHits.incrementAndGet()
                val body = "ok".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            createContext("/redirect") { exchange ->
                exchange.responseHeaders.add("Location", "http://localhost:$port/target")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
            createContext("/target") { exchange ->
                targetHits.incrementAndGet()
                exchange.sendResponseHeaders(200, -1)
                exchange.close()
            }
            start()
        }
    }

    @AfterTest
    fun stop() {
        server.stop(0)
        PrivateNetworkGuardSettings.clearAllowLoopbackChoice()
    }

    private fun onlyLoopbackIp(followRedirects: Boolean = false) = WebDataFetchingConfiguration(
        url = UrlConfiguration(hosts = AllowList(whitelist = listOf("127.0.0.1"))),
        engine = RequestEngineConfiguration(followRedirects = followRedirects),
    )

    @Test
    fun `allows a host on the allow-list`() = runTest {
        val client = WebDataFetcher("url-allow-list-allowed", onlyLoopbackIp()).httpClient
        assertEquals("ok", client.get("http://127.0.0.1:$port/ok").bodyAsText())
        assertEquals(1, okHits.get())
    }

    @Test
    fun `refuses a host that is not on the allow-list without requesting it`() = runTest {
        val client = WebDataFetcher("url-allow-list-refused", onlyLoopbackIp()).httpClient
        val error = assertFailsWith<IllegalArgumentException> { client.get("http://localhost:$port/ok") }
        assertEquals("Host is not allowed: localhost", error.message)
        assertEquals(0, okHits.get())
    }

    @Test
    fun `refuses a redirect hop to a host that is not on the allow-list`() = runTest {
        val client = WebDataFetcher("url-allow-list-redirect", onlyLoopbackIp(followRedirects = true)).httpClient
        assertFailsWith<IllegalArgumentException> { client.get("http://127.0.0.1:$port/redirect") }
        assertEquals(0, targetHits.get(), "the redirect target must not be requested")
    }

    @Test
    fun `applies an allow-list configured after the client was built`() = runTest {
        val id = "url-allow-list-late"
        val client = WebDataFetcher(id).httpClient
        WebDataFetcherManager.appendConfiguration(
            id,
            WebDataFetchingConfiguration(url = UrlConfiguration(hosts = AllowList(blacklist = listOf("127.0.0.1")))),
        )
        assertFailsWith<IllegalArgumentException> { client.get("http://127.0.0.1:$port/ok") }
        assertEquals(0, okHits.get())
    }
}
