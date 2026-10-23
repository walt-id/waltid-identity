package id.walt.ktornotifications

import com.sun.net.httpserver.HttpServer
import id.walt.ktornotifications.core.KtorSessionNotifications.VerificationSessionWebhookNotification
import id.walt.ktornotifications.core.KtorSessionUpdate
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WebhookNotifierTest {

    private val update = KtorSessionUpdate(target = "session", event = "test", session = buildJsonObject {})

    @BeforeTest
    @AfterTest
    fun resetGuard() {
        // JVM test tasks set this so ordinary suites can bind 127.0.0.1. These cases assert the default refusal.
        System.clearProperty("walt.ssrf.allowLoopback")
        applyNetworkFetchGuard(allowLoopback = false, allowPrivateNetworks = false, trustedHostSuffixes = emptySet())
    }

    @Test
    fun `a loopback webhook is refused before the server is contacted`() = runTest {
        val hits = AtomicInteger()
        val server = localServer(hits)
        try {
            val failure = assertFailsWith<IllegalArgumentException> {
                WebhookNotifier.notify(update, webhook("http://127.0.0.1:${server.address.port}/hook"))
            }
            assertEquals("Webhook destination is not allowed", failure.message)
            assertEquals(0, hits.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `a private address is refused`() = runTest {
        val failure = assertFailsWith<IllegalArgumentException> {
            WebhookNotifier.notify(update, webhook("http://10.0.0.5/hook"))
        }
        assertEquals("Webhook destination is not allowed", failure.message)
    }

    @Test
    fun `cloud metadata stays refused when loopback and private networks are allowed`() = runTest {
        applyNetworkFetchGuard(allowLoopback = true, allowPrivateNetworks = true, trustedHostSuffixes = emptySet())
        val failure = assertFailsWith<IllegalArgumentException> {
            WebhookNotifier.notify(update, webhook("http://169.254.169.254/latest/meta-data/"))
        }
        assertEquals("Webhook destination is not allowed", failure.message)
    }

    @Test
    fun `a redirect from an allowed host to a private address is refused`() = runTest {
        applyNetworkFetchGuard(allowLoopback = true, allowPrivateNetworks = false, trustedHostSuffixes = emptySet())
        val hits = AtomicInteger()
        val server = localServer(hits) { exchange ->
            exchange.responseHeaders.add("Location", "http://10.0.0.5/secret")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        try {
            val failure = assertFailsWith<IllegalArgumentException> {
                WebhookNotifier.notify(update, webhook("http://127.0.0.1:${server.address.port}/hook"))
            }
            assertEquals("Webhook destination is not allowed", failure.message)
            assertEquals(1, hits.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `loopback is delivered when the deployment allows it`() = runTest {
        applyNetworkFetchGuard(allowLoopback = true, allowPrivateNetworks = false, trustedHostSuffixes = emptySet())
        val hits = AtomicInteger()
        val server = localServer(hits)
        try {
            WebhookNotifier.notify(update, webhook("http://127.0.0.1:${server.address.port}/hook"))
            assertEquals(1, hits.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `a trusted hostname may resolve to loopback`() = runTest {
        applyNetworkFetchGuard(allowLoopback = false, allowPrivateNetworks = false, trustedHostSuffixes = setOf("localhost"))
        val hits = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/hook") { exchange ->
            hits.incrementAndGet()
            val body = "ok".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            WebhookNotifier.notify(update, webhook("http://localhost:${server.address.port}/hook"))
            assertEquals(1, hits.get())
        } finally {
            server.stop(0)
        }
    }

    private fun webhook(url: String) = VerificationSessionWebhookNotification(url = Url(url))

    private fun localServer(
        hits: AtomicInteger,
        handler: com.sun.net.httpserver.HttpHandler? = null,
    ): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/hook") { exchange ->
            hits.incrementAndGet()
            if (handler != null) {
                handler.handle(exchange)
            } else {
                val body = "ok".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
        }
        server.start()
        return server
    }
}
