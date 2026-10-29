package id.walt.ktornotifications

import com.sun.net.httpserver.HttpServer
import id.walt.ktornotifications.core.KtorSessionNotifications.VerificationSessionWebhookNotification
import id.walt.ktornotifications.core.KtorSessionUpdate
import id.walt.webdatafetching.ssrf.PrivateNetworkGuardSettings
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
import kotlin.test.assertTrue

class WebhookNotifierTest {

    private val update = KtorSessionUpdate(target = "session", event = "test", session = buildJsonObject {})

    companion object {
        private const val BLOCKED = "Webhook destination is not allowed (network-fetch-guard.conf)"
    }

    @BeforeTest
    @AfterTest
    fun resetGuard() {
        // JVM test tasks set this so ordinary suites can bind 127.0.0.1. These cases assert the default refusal.
        System.clearProperty("walt.ssrf.allowLoopback")
        guard(allowLoopback = false)
    }

    @Test
    fun `a loopback webhook is refused before the server is contacted`() = runTest {
        val hits = AtomicInteger()
        val server = localServer(hits)
        try {
            val failure = assertFailsWith<IllegalArgumentException> {
                WebhookNotifier.notify(update, webhook("http://127.0.0.1:${server.address.port}/hook"))
            }
            assertEquals(BLOCKED, failure.message)
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
        assertEquals(BLOCKED, failure.message)
    }

    @Test
    fun `cloud metadata stays refused when loopback and private networks are allowed`() = runTest {
        guard(allowLoopback = true, allowPrivateNetworks = true)
        val failure = assertFailsWith<IllegalArgumentException> {
            WebhookNotifier.notify(update, webhook("http://169.254.169.254/latest/meta-data/"))
        }
        assertEquals(BLOCKED, failure.message)
    }

    @Test
    fun `a redirect is returned and not followed`() = runTest {
        guard(allowLoopback = true)
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
            assertTrue(failure.message!!.contains("HTTP 302"), failure.message)
            assertEquals(1, hits.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `loopback is delivered when the deployment allows it`() = runTest {
        guard(allowLoopback = true)
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
    fun `a configured loopback deny wins over the test system property`() = runTest {
        System.setProperty("walt.ssrf.allowLoopback", "true")
        guard(allowLoopback = false)
        val hits = AtomicInteger()
        val server = localServer(hits)
        try {
            val failure = assertFailsWith<IllegalArgumentException> {
                WebhookNotifier.notify(update, webhook("http://127.0.0.1:${server.address.port}/hook"))
            }
            assertEquals(BLOCKED, failure.message)
            assertEquals(0, hits.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `an unresolvable host is a connection failure`() = runTest {
        val failure = assertFailsWith<IllegalArgumentException> {
            WebhookNotifier.notify(update, webhook("http://no-such-host.invalid/hook"))
        }
        assertTrue(failure.message!!.startsWith("Could not connect"), failure.message)
    }

    @Test
    fun `a trusted hostname may resolve to loopback`() = runTest {
        guard(allowLoopback = false, trustedHostSuffixes = setOf("localhost"))
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

    private fun guard(
        allowLoopback: Boolean,
        allowPrivateNetworks: Boolean = false,
        trustedHostSuffixes: Set<String> = emptySet(),
    ) {
        PrivateNetworkGuardSettings.allowLoopback = allowLoopback
        PrivateNetworkGuardSettings.allowPrivateNetworks = allowPrivateNetworks
        PrivateNetworkGuardSettings.trustedHostSuffixes = trustedHostSuffixes
    }

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
