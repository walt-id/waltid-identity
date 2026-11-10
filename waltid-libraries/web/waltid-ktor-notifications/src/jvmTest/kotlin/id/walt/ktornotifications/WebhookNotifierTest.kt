package id.walt.ktornotifications

import com.sun.net.httpserver.HttpServer
import id.walt.ktornotifications.core.KtorSessionNotifications.WebhookRetryPolicy
import id.walt.ktornotifications.core.KtorSessionNotifications.VerificationSessionWebhookNotification
import id.walt.ktornotifications.core.KtorSessionUpdate
import id.walt.webdatafetching.ssrf.PrivateNetworkGuardSettings
import io.ktor.http.Url
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebhookNotifierTest {

    private val update = KtorSessionUpdate(target = "session", event = "test", session = buildJsonObject {})
    private lateinit var outboxDirectory: Path

    companion object {
        private const val BLOCKED = "Webhook destination is not allowed (network-fetch-guard.conf)"
    }

    @BeforeTest
    fun denyLoopback() = runBlocking {
        WebhookNotifier.stop()
        outboxDirectory = Files.createTempDirectory("webhook-notifier-test-")
        WebhookNotifier.start(outboxDirectory)
        System.clearProperty("walt.ssrf.allowLoopback")
        guard(allowLoopback = false)
    }

    @AfterTest
    fun releaseLoopbackChoice() = runBlocking {
        try {
            WebhookNotifier.stop()
        } finally {
            outboxDirectory.toFile().deleteRecursively()
            System.clearProperty("walt.ssrf.allowLoopback")
            PrivateNetworkGuardSettings.clearAllowLoopbackChoice()
            PrivateNetworkGuardSettings.allowPrivateNetworks = false
            PrivateNetworkGuardSettings.trustedHostSuffixes = emptySet()
        }
    }

    @Test
    fun `a loopback webhook is refused before the server is contacted`() = runBlocking {
        val hits = AtomicInteger()
        val server = localServer(hits)
        try {
            val id = WebhookNotifier.notify(update, webhook("http://127.0.0.1:${server.address.port}/hook"))
            assertTrue(awaitDeadLetter(id).lastError.contains(BLOCKED))
            assertEquals(0, hits.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `a private address is refused`() = runBlocking {
        val id = WebhookNotifier.notify(update, webhook("http://10.0.0.5/hook"))
        assertTrue(awaitDeadLetter(id).lastError.contains(BLOCKED))
    }

    @Test
    fun `cloud metadata stays refused when loopback and private networks are allowed`() = runBlocking {
        guard(allowLoopback = true, allowPrivateNetworks = true)
        val id = WebhookNotifier.notify(update, webhook("http://169.254.169.254/latest/meta-data/"))
        assertTrue(awaitDeadLetter(id).lastError.contains(BLOCKED))
    }

    @Test
    fun `a redirect is returned and not followed`() = runBlocking {
        guard(allowLoopback = true)
        val hits = AtomicInteger()
        val server = localServer(hits) { exchange ->
            exchange.responseHeaders.add("Location", "http://10.0.0.5/secret")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        try {
            val id = WebhookNotifier.notify(update, webhook("http://127.0.0.1:${server.address.port}/hook"))
            assertTrue(awaitDeadLetter(id).lastError.contains("HTTP 302"))
            assertEquals(1, hits.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `loopback is delivered when the deployment allows it`() = runBlocking {
        guard(allowLoopback = true)
        val hits = AtomicInteger()
        val server = localServer(hits)
        try {
            val id = WebhookNotifier.notify(update, webhook("http://127.0.0.1:${server.address.port}/hook"))
            awaitDelivered(id)
            assertEquals(1, hits.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `a configured loopback deny wins over the test system property`() = runBlocking {
        System.setProperty("walt.ssrf.allowLoopback", "true")
        guard(allowLoopback = false)
        val hits = AtomicInteger()
        val server = localServer(hits)
        try {
            val id = WebhookNotifier.notify(update, webhook("http://127.0.0.1:${server.address.port}/hook"))
            assertTrue(awaitDeadLetter(id).lastError.contains(BLOCKED))
            assertEquals(0, hits.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `an unresolvable host is a connection failure`() = runBlocking {
        val id = WebhookNotifier.notify(
            update,
            webhook("http://no-such-host.invalid/hook", WebhookRetryPolicy(maxAttempts = 1)),
        )
        assertTrue(awaitDeadLetter(id).lastError.contains("no-such-host.invalid"))
    }

    @Test
    fun `a trusted hostname may resolve to loopback`() = runBlocking {
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
            val id = WebhookNotifier.notify(update, webhook("http://localhost:${server.address.port}/hook"))
            awaitDelivered(id)
            assertEquals(1, hits.get())
        } finally {
            server.stop(0)
        }
    }

    private fun webhook(url: String, retryPolicy: WebhookRetryPolicy = WebhookRetryPolicy()) =
        VerificationSessionWebhookNotification(url = Url(url), retryPolicy = retryPolicy)

    private suspend fun awaitDeadLetter(deliveryId: String): WebhookDeadLetter = withTimeout(10_000) {
        while (true) {
            WebhookNotifier.deadLetters().firstOrNull { it.deliveryId == deliveryId }?.let { return@withTimeout it }
            delay(25)
        }
        error("unreachable")
    }

    private suspend fun awaitDelivered(deliveryId: String) = withTimeout(10_000) {
        val pending = outboxDirectory.resolve("pending/$deliveryId.json")
        while (Files.exists(pending)) delay(25)
    }

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
