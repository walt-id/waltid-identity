package id.walt.commons

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FetchBinaryFileTest {

    private val payload = byteArrayOf(0x84.toByte(), 0x43, 0xA1.toByte(), 0x01, 0x26)
    private val hits = ConcurrentHashMap<String, AtomicInteger>()
    private lateinit var server: HttpServer
    private val port get() = server.address.port

    @BeforeTest
    fun startServer() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange ->
            hits.computeIfAbsent(exchange.requestURI.path) { AtomicInteger() }.incrementAndGet()
            when (exchange.requestURI.path) {
                "/vical" -> exchange.respond(payload)
                "/redirect" -> {
                    exchange.responseHeaders.add("Location", "/vical")
                    exchange.sendResponseHeaders(302, -1)
                }
                "/large" -> exchange.respond(ByteArray(LIMIT + 1))
                "/large-chunked" -> {
                    exchange.sendResponseHeaders(200, 0)
                    exchange.responseBody.write(ByteArray(LIMIT + 1))
                }
                else -> exchange.sendResponseHeaders(404, -1)
            }
            exchange.close()
        }
        server.start()
    }

    @AfterTest
    fun stopServer() = server.stop(0)

    @Test
    fun `non-http schemes are rejected before any read`() {
        listOf("file:///etc/hostname", "FILE:///etc/hostname", "jar:file:///tmp/x.jar!/a", "ftp://example.org/vical", "example.org/vical")
            .forEach { url ->
                val e = assertFailsWith<IllegalArgumentException>(url) { fetchBinaryFile(url) }
                assertContains(e.message!!, "only http and https URLs are allowed")
            }
    }

    @Test
    fun `loopback, wildcard and metadata destinations are refused without connecting`() {
        listOf(
            "http://127.0.0.1:$port/vical",
            "http://localhost:$port/vical",
            "http://[::1]:$port/vical",
            "http://0.0.0.0:$port/vical",
            "http://169.254.169.254/latest/meta-data/",
            "http://[fd00:ec2::254]/latest/meta-data/",
        ).forEach { url ->
            val e = assertFailsWith<IllegalArgumentException>(url) { fetchBinaryFile(url) }
            assertContains(e.message!!, "destination not allowed")
        }
        assertEquals(emptyMap(), hits)
    }

    @Test
    fun `an allowed destination returns the body unchanged`() {
        assertContentEquals(payload, fetchFromTestServer("/vical"))
    }

    @Test
    fun `redirects are not followed`() {
        val e = assertFailsWith<IllegalArgumentException> { fetchFromTestServer("/redirect") }
        assertContains(e.message!!, "HTTP 302")
        assertNull(hits["/vical"])
    }

    @Test
    fun `responses over the size cap are rejected with or without a content length`() {
        listOf("/large", "/large-chunked").forEach { path ->
            val e = assertFailsWith<IllegalArgumentException>(path) { fetchFromTestServer(path) }
            assertContains(e.message!!, "response too large")
        }
    }

    @Test
    fun `non-success responses are rejected`() {
        val e = assertFailsWith<IllegalArgumentException> { fetchFromTestServer("/missing") }
        assertContains(e.message!!, "HTTP 404")
    }

    /** Production policy, except that the loopback test server is reachable. */
    private fun fetchFromTestServer(path: String) = fetchBinaryFile(
        url = "http://127.0.0.1:$port$path",
        isBlockedAddress = { !it.isLoopbackAddress && isBlockedFetchDestination(it) },
        maxBytes = LIMIT.toLong(),
    )

    private fun HttpExchange.respond(body: ByteArray) {
        sendResponseHeaders(200, body.size.toLong())
        responseBody.write(body)
    }

    private companion object {
        const val LIMIT = 1024
    }
}
