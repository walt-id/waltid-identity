package id.walt.webdatafetching

import com.sun.net.httpserver.HttpServer
import id.walt.webdatafetching.config.TimeoutConfiguration
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class SecureHttpDefaultsTest {

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
