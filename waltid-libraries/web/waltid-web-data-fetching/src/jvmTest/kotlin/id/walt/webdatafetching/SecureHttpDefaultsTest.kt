package id.walt.webdatafetching

import com.sun.net.httpserver.HttpServer
import id.walt.webdatafetching.config.LoggingConfiguration
import id.walt.webdatafetching.config.RequestEngineConfiguration
import id.walt.webdatafetching.config.TimeoutConfiguration
import id.walt.webdatafetching.config.headerIsSanitized
import id.walt.webdatafetching.ssrf.PrivateNetworkGuardSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SecureHttpDefaultsTest {

    // An assigned allowLoopback wins over walt.ssrf.allowLoopback, so a property alone would depend on which
    // guard test ran last in this JVM.
    @BeforeTest
    fun allowLoopbackForLocalServers() {
        PrivateNetworkGuardSettings.allowLoopback = true
    }

    @AfterTest
    fun restoreLoopbackChoice() {
        PrivateNetworkGuardSettings.clearAllowLoopbackChoice()
    }

    @Test
    fun `a declared content length over the cap is rejected`() = runTest {
        val hits = AtomicInteger()
        val server = localServer { exchange ->
            hits.incrementAndGet()
            val body = ByteArray(64)
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.write(body)
            exchange.close()
        }
        server.start()
        try {
            val error = assertFailsWith<DataFetchingException> {
                fetcher(maxResponseBytes = 16).fetch<String>(server.url("/big"))
            }
            assertTrue(error.causes().any { it is ResponseBodyTooLargeException })
            assertEquals(1, hits.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `a chunked body over the cap is rejected`() = runTest {
        val server = localServer { exchange ->
            exchange.sendResponseHeaders(200, 0)
            val chunk = ByteArray(32)
            repeat(8) {
                exchange.responseBody.write(chunk)
            }
            exchange.close()
        }
        server.start()
        try {
            val error = assertFailsWith<DataFetchingException> {
                fetcher(maxResponseBytes = 16).fetch<String>(server.url("/chunked"))
            }
            assertTrue(error.causes().any { it is ResponseBodyTooLargeException })
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `redirects are not followed unless the configuration asks for them`() = runTest {
        val targetHits = AtomicInteger()
        val server = localServer { exchange ->
            when (exchange.requestURI.path) {
                "/next" -> {
                    targetHits.incrementAndGet()
                    exchange.sendResponseHeaders(204, -1)
                }
                else -> {
                    exchange.responseHeaders.add("Location", "/next")
                    exchange.sendResponseHeaders(302, -1)
                }
            }
            exchange.close()
        }
        server.start()
        try {
            val result = fetcher().fetch<String>(server.url("/start"))
            assertEquals(302, result.status)
            assertEquals(0, targetHits.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `a server error is not downloaded again`() = runTest {
        val hits = AtomicInteger()
        val server = localServer { exchange ->
            hits.incrementAndGet()
            val body = "no".toByteArray()
            exchange.sendResponseHeaders(500, body.size.toLong())
            exchange.responseBody.write(body)
            exchange.close()
        }
        server.start()
        try {
            val result = fetcher().fetch<String>(server.url("/fail"))
            assertEquals(500, result.status)
            assertEquals(1, hits.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `a request that exceeds the configured timeout fails`() = runTest {
        val server = localServer { exchange ->
            Thread.sleep(2_000)
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
        }
        server.start()
        try {
            assertFailsWith<DataFetchingException> {
                WebDataFetcher(
                    "timeout",
                    WebDataFetchingConfiguration(timeouts = TimeoutConfiguration(requestTimeout = 200.milliseconds)),
                ).fetch<String>(server.url("/slow"))
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `a body of exactly the cap is accepted`() = runTest {
        val body = ByteArray(16) { it.toByte() }
        val server = localServer { exchange ->
            exchange.responseHeaders.add("Content-Type", "text/plain")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.write(body)
            exchange.close()
        }
        server.start()
        try {
            val result = fetcher(maxResponseBytes = 16).fetch<String>(server.url("/exact"))
            assertEquals(16, result.body.length)
            assertEquals(200, result.status)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `a non positive cap disables the limit`() = runTest {
        val body = ByteArray(64)
        val server = localServer { exchange ->
            exchange.responseHeaders.add("Content-Type", "text/plain")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.write(body)
            exchange.close()
        }
        server.start()
        try {
            val result = fetcher(maxResponseBytes = 0).fetch<String>(server.url("/uncapped"))
            assertEquals(64, result.body.length)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `credential headers are redacted and a normal header is kept`() = runTest {
        val logs = StringBuilder()
        val server = localServer { exchange ->
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
        }
        server.start()
        val client = HttpClient(Java) {
            WebDataFetchingConfiguration().applyConfigurationToHttpClient(
                this,
                object : Logger {
                    override fun log(message: String) {
                        logs.append(message).append('\n')
                    }
                },
            )
        }
        try {
            client.get(server.url("/log")) {
                header("authorization", "Bearer secret-token")
                header("Cookie", "session=secret-cookie")
                header("X-Request-Id", "visible-id")
            }
            val text = logs.toString()
            assertTrue(text.contains("visible-id"), text)
            assertTrue(!text.contains("secret-token"), text)
            assertTrue(!text.contains("secret-cookie"), text)
        } finally {
            client.close()
            server.stop(0)
        }
    }

    @Test
    fun `sanitized header matching ignores case`() {
        val names = LoggingConfiguration.SENSITIVE_HEADERS
        assertTrue(headerIsSanitized("authorization", names))
        assertTrue(headerIsSanitized("SET-COOKIE", names))
        assertTrue(!headerIsSanitized("X-Request-Id", names))
    }

    @Test
    fun `a body that drips after the headers fails on the request timeout`() = runTest {
        val server = localServer { exchange ->
            exchange.sendResponseHeaders(200, 100)
            val body = exchange.responseBody
            repeat(20) {
                body.write(byteArrayOf(1))
                body.flush()
                Thread.sleep(200)
            }
            exchange.close()
        }
        server.start()
        val started = System.nanoTime()
        try {
            val error = assertFailsWith<DataFetchingException> {
                WebDataFetcher(
                    "drip",
                    WebDataFetchingConfiguration(timeouts = TimeoutConfiguration(requestTimeout = 1.seconds)),
                ).fetch<String>(server.url("/drip"))
            }
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            assertTrue(elapsedMs < 3_000, "elapsed ${elapsedMs}ms")
            assertTrue(error.causes().any { it.message?.contains("timeout", ignoreCase = true) == true }, error.toString())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `an unset timeout layer keeps a lower-layer timeout`() {
        val longTimeout = TimeoutConfiguration(
            connectTimeout = 120.seconds,
            requestTimeout = 120.seconds,
            socketTimeout = 120.seconds,
        )
        val withTimeout = WebDataFetchingConfiguration(timeouts = longTimeout)
        assertEquals(null, WebDataFetchingConfiguration().timeouts)
        assertEquals(120.seconds, withTimeout.mergeWith(WebDataFetchingConfiguration()).timeouts?.requestTimeout)
        assertEquals(
            120.seconds,
            withTimeout.mergeWith(
                WebDataFetchingConfiguration(engine = RequestEngineConfiguration(followRedirects = true)),
            ).timeouts?.requestTimeout,
        )
    }

    @Test
    fun `an explicit default cap does not replace a custom lower layer`() {
        val custom = WebDataFetchingConfiguration(maxResponseBytes = 100)
        val unset = WebDataFetchingConfiguration()
        assertEquals(100, custom.mergeWith(unset).maxResponseBytes)
        assertEquals(50, custom.mergeWith(WebDataFetchingConfiguration(maxResponseBytes = 50)).maxResponseBytes)
        assertEquals(
            100,
            custom.mergeWith(
                WebDataFetchingConfiguration(maxResponseBytes = WebDataFetchingConfiguration.DEFAULT_MAX_RESPONSE_BYTES),
            ).maxResponseBytes,
        )
    }

    private fun Throwable.causes(): Sequence<Throwable> = generateSequence(this) { it.cause }

    private fun fetcher(maxResponseBytes: Long = 16) = WebDataFetcher(
        "secure-defaults",
        WebDataFetchingConfiguration(maxResponseBytes = maxResponseBytes),
    )

    private fun localServer(handler: com.sun.net.httpserver.HttpHandler): HttpServer =
        HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
            createContext("/", handler)
        }

    private fun HttpServer.url(path: String) = Url("http://127.0.0.1:${address.port}$path")
}
