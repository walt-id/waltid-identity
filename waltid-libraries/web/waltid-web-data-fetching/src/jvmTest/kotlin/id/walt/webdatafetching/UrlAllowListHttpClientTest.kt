package id.walt.webdatafetching

import com.sun.net.httpserver.HttpServer
import id.walt.webdatafetching.config.AllowList
import id.walt.webdatafetching.config.CacheConfiguration
import id.walt.webdatafetching.config.HttpEngine
import id.walt.webdatafetching.config.RequestEngineConfiguration
import id.walt.webdatafetching.config.url.UrlConfiguration
import id.walt.webdatafetching.ssrf.BlockedAddressException
import id.walt.webdatafetching.ssrf.ConnectTimeDns
import id.walt.webdatafetching.ssrf.PrivateNetworkGuardSettings
import io.ktor.client.engine.okhttp.OkHttpConfig
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.Url
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

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
                exchange.responseHeaders.add("Content-Type", "text/plain")
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
                val body = "target".toByteArray()
                exchange.responseHeaders.add("Content-Type", "text/plain")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
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
        val error = assertFailsWith<IllegalArgumentException> { client.get("http://127.0.0.1:$port/redirect") }
        assertEquals("Host is not allowed: localhost", error.message)
        assertEquals(0, targetHits.get(), "the redirect target must not be requested")
    }

    @Test
    fun `helpers and cache hits obey changes to the live allow-list`() = runTest {
        val id = "url-allow-list-late"
        val denied = WebDataFetchingConfiguration(url = UrlConfiguration(hosts = AllowList(whitelist = emptyList())))
        val url = Url("http://127.0.0.1:$port/ok")
        WebDataFetcher(id, denied.copy(cache = CacheConfiguration.Example)).use { fetcher ->
            assertFailsWith<IllegalArgumentException> { fetcher.httpClient.get(url) }
            WebDataFetcherManager.appendConfiguration(id, onlyLoopbackIp())
            assertEquals("ok", fetcher.fetch<String>(url).body)
            assertTrue(fetcher.fetch<String>(url).cached)
            assertEquals("ok", fetcher.rawFetch(url).bodyAsText())
            assertEquals("ok", fetcher.sendForm(url.toString(), Parameters.Empty).bodyAsText())
            WebDataFetcherManager.appendConfiguration(id, denied)
            assertFailsWith<IllegalArgumentException> { fetcher.fetch<String>(url) }
            assertFailsWith<IllegalArgumentException> { fetcher.httpClient.get(url) }
            assertEquals(3, okHits.get())
        }
    }

    @Test
    fun `a cached redirect is rechecked after the destination policy changes`() = runTest {
        val id = "url-policy-cached-redirect"
        val configuration = WebDataFetchingConfiguration(
            cache = CacheConfiguration.Example,
            engine = RequestEngineConfiguration(followRedirects = true),
        )
        val url = Url("http://127.0.0.1:$port/redirect")
        WebDataFetcher(id, configuration).use { fetcher ->
            assertEquals("target", fetcher.fetch<String>(url).body)
            WebDataFetcherManager.appendConfiguration("unrelated-fetcher", onlyLoopbackIp())
            assertTrue(fetcher.fetch<String>(url).cached)
            WebDataFetcherManager.appendConfiguration(id, onlyLoopbackIp(followRedirects = true))
            val error = assertFailsWith<DataFetchingException> { fetcher.fetch<String>(url) }
            assertEquals("Host is not allowed: localhost", error.cause?.message)
            assertEquals(1, targetHits.get())
        }
    }

    @Test
    fun `rejects a disallowed name before lookup on initial and redirected requests`() = runTest {
        server.createContext("/other-name") { exchange ->
            exchange.responseHeaders.add("Location", "https://unlisted.invalid/list")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        WebDataFetcher("url-policy-order", onlyLoopbackIp(followRedirects = true)).use { fetcher ->
            for (url in listOf("https://unlisted.invalid/list", "http://127.0.0.1:$port/other-name")) {
                val error = assertFailsWith<IllegalArgumentException> { fetcher.httpClient.get(url) }
                assertEquals("Host is not allowed: unlisted.invalid", error.message)
            }
        }
    }

    @Test
    fun `status and JWKS clients validate the connecting address with a configured CIO engine`() {
        val enterprise = WebDataFetchingConfiguration(http = HttpEngine.CIO)
        val ids = listOf(WebDataFetcherId.STATUS_POLICY, WebDataFetcherId.STATUS_POLICY2, WebDataFetcherId.JWKS_KEY_PROVIDER)
        for (id in ids) {
            WebDataFetcher(id, enterprise).use { fetcher ->
                val engine = fetcher.httpClient.engine.config as OkHttpConfig
                assertSame(ConnectTimeDns, engine.dns)
                assertEquals(Proxy.NO_PROXY, engine.proxy)
                assertNull(fetcher.httpClient.pluginOrNull(Logging))
            }
        }
        val proxy = RequestEngineConfiguration.HttpProxyConfiguration(Url("http://proxy.invalid"))
        assertFailsWith<IllegalArgumentException> {
            WebDataFetcher(WebDataFetcherId.STATUS_POLICY2, enterprise.copy(engine = RequestEngineConfiguration(proxy = proxy)))
        }
    }

    @Test
    fun `credential fetch logs omit query values on success and refusal`() = runTest {
        val marker = "synthetic-private-query"
        val output = ByteArrayOutputStream()
        val original = System.err
        PrintStream(output).use { capture ->
            System.setErr(capture)
            try {
                WebDataFetcher(WebDataFetcherId.JWKS_KEY_PROVIDER).use { fetcher ->
                    val url = "http://127.0.0.1:$port/ok?fixture=$marker"
                    assertEquals("ok", fetcher.httpClient.get(url).bodyAsText())
                    PrivateNetworkGuardSettings.allowLoopback = false
                    assertFailsWith<BlockedAddressException> { fetcher.httpClient.get(url) }
                }
            } finally {
                System.setErr(original)
            }
        }
        val logged = output.toByteArray().decodeToString()
        assertTrue("destination address is blocked" in logged)
        assertFalse(marker in logged)
    }
}
