package id.walt.wallet2.mobile

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.URLProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Real Android HTTP transport: MockEngine and host URLConnection cannot reproduce the platform race. */
class RegistryImageFetchTest {
    @Test
    fun parentCancellationDuringBodyReadPropagatesAndReleasesSocket() = runBlocking {
        StalledResponse().use { server ->
            client().use { client ->
                var returnedAfterCancellation = false
                val request = async(Dispatchers.IO) {
                    fetchRegistryIconBytes(server.url, client)
                    returnedAfterCancellation = true
                }
                awaitBlockedBodyRead()
                withTimeout(2_000) { request.cancelAndJoin() }
                assertFalse(returnedAfterCancellation, "Cancellation must not become a missing optional image")
                server.assertPeerClosed()
                assertSuccessfulRequest(client)
            }
        }
    }

    @Test
    fun outerDeadlineDuringBodyReadPreservesTimeoutBoundaryAndReleasesSocket() = runBlocking {
        StalledResponse().use { server ->
            client().use { client ->
                var returnedInsideDeadline = false
                val request = async(Dispatchers.IO) {
                    withTimeoutOrNull(1_000) {
                        fetchRegistryIconBytes(server.url, client)
                        returnedInsideDeadline = true
                    }
                }
                awaitBlockedBodyRead()
                assertNull(withTimeout(3_000) { request.await() })
                assertFalse(returnedInsideDeadline)
                server.assertPeerClosed()
            }
        }
    }

    @Test
    fun requestTimeoutDuringBodyReadReturnsFallbackAndReleasesSocket() = runBlocking {
        StalledResponse().use { server ->
            client(requestTimeout = 1_000).use { client ->
                val request = async(Dispatchers.IO) { fetchRegistryIconBytes(server.url, client) }
                awaitBlockedBodyRead()
                assertNull(withTimeout(3_000) { request.await() })
                server.assertPeerClosed()
            }
        }
    }

    @Test
    fun socketTimeoutDuringBodyReadReturnsFallbackAndReleasesSocket() = runBlocking {
        StalledResponse().use { server ->
            client(socketTimeout = 1_000).use { client ->
                val request = async(Dispatchers.IO) { fetchRegistryIconBytes(server.url, client) }
                awaitBlockedBodyRead()
                assertNull(withTimeout(3_000) { request.await() })
                server.assertPeerClosed()
            }
        }
    }

    @Test
    fun cancellationBeforeResponseHeadersReleasesSocket() = runBlocking {
        StalledResponse(sendHeaders = false).use { server ->
            client().use { client ->
                var returnedAfterCancellation = false
                val request = async(Dispatchers.IO) {
                    fetchRegistryIconBytes(server.url, client)
                    returnedAfterCancellation = true
                }
                withTimeout(3_000) { while (!server.requestReceived.isDone) delay(10) }
                withTimeout(2_000) { request.cancelAndJoin() }
                assertFalse(returnedAfterCancellation)
                server.assertPeerClosed()
            }
        }
    }

    @Test
    fun completedBodyIsReturnedAndReleased() = runBlocking {
        client().use { assertSuccessfulRequest(it) }
    }

    @Test
    fun unsuccessfulStatusDoesNotDrainStalledBody() = runBlocking {
        StalledResponse(status = "404 Not Found").use { server ->
            client().use { client ->
                assertNull(withTimeout(2_000) { fetchRegistryIconBytes(server.url, client) })
                server.assertPeerClosed()
            }
        }
    }

    @Test
    fun oversizedContentLengthDoesNotDrainStalledBody() = runBlocking {
        StalledResponse(length = MaxRegistryIconBytes + 1).use { server ->
            client().use { client ->
                assertNull(withTimeout(2_000) { fetchRegistryIconBytes(server.url, client) })
                server.assertPeerClosed()
            }
        }
    }

    @Test
    fun unknownLengthBodyStopsAtSizeLimitAndReleasesSocket() = runBlocking {
        StalledResponse(length = null, body = ByteArray(MaxRegistryIconBytes + 1)).use { server ->
            client().use { client ->
                assertNull(withTimeout(3_000) { fetchRegistryIconBytes(server.url, client) })
                server.assertPeerClosed()
            }
        }
    }

    @Test
    fun nonHttpsUrlIsRejectedWithoutSendingRequest() = runBlocking {
        StalledResponse().use { server ->
            client().use { client ->
                assertNull(fetchRegistryIconBytes(server.url.replace("https:", "http:"), client))
                assertFalse(server.requestReceived.isDone)
            }
        }
    }

    private suspend fun assertSuccessfulRequest(client: HttpClient) {
        val bytes = byteArrayOf(1, 2, 3, 4)
        StalledResponse(length = bytes.size, body = bytes).use { server ->
            assertContentEquals(bytes, withTimeout(3_000) { fetchRegistryIconBytes(server.url, client) })
            server.assertPeerClosed()
        }
    }

    private fun client(requestTimeout: Long = 5_000, socketTimeout: Long = 5_000): HttpClient =
        createRegistryImageHttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = requestTimeout
                socketTimeoutMillis = socketTimeout
            }
            // Keep the production HTTPS guard; only this loopback test client's wire protocol changes.
            install(createClientPlugin("RegistryImageLoopback") {
                onRequest { request, _ ->
                    check(request.url.host == "127.0.0.1")
                    request.url.protocol = URLProtocol.HTTP
                }
            })
        }

    private suspend fun awaitBlockedBodyRead() = withTimeout(3_000) {
        // Cancel only after the real reader is inside socket I/O, not after an arbitrary sleep.
        while (Thread.getAllStackTraces().values.none { stack ->
                stack.any { it.className.contains("RawSourceChannel") || it.className.contains("OkHttpEngine") } &&
                    stack.any { it.className.contains("Socket") && it.methodName.contains("read", ignoreCase = true) }
            }) delay(10)
    }

    private class StalledResponse(
        status: String = "200 OK",
        length: Int? = 100,
        body: ByteArray = byteArrayOf(1),
        sendHeaders: Boolean = true,
    ) : AutoCloseable {
        private val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        private val socket = AtomicReference<Socket>()
        val requestReceived = CompletableFuture<Unit>()
        private val peerClosed = CompletableFuture<Unit>()
        val url = "https://127.0.0.1:${server.localPort}/icon"
        private val worker = thread(name = "registry-image-loopback", isDaemon = true) {
            try {
                server.accept().use { connection ->
                    socket.set(connection)
                    connection.soTimeout = 8_000
                    val input = connection.getInputStream().bufferedReader()
                    while (true) {
                        val line = input.readLine() ?: error("Client closed before request headers")
                        if (line.isEmpty()) break
                    }
                    requestReceived.complete(Unit)
                    if (sendHeaders) {
                        val output = connection.getOutputStream()
                        val headers = "HTTP/1.1 $status\r\nConnection: close\r\n" +
                            (length?.let { "Content-Length: $it\r\n" } ?: "") + "\r\n"
                        output.write(headers.toByteArray())
                        output.write(body)
                        output.flush()
                    }
                    // The server holds the body open. Only client cleanup can close this socket.
                    try {
                        assertEquals(-1, input.read())
                    } catch (_: SocketException) {
                        // A TCP reset also proves client-side release.
                    }
                    peerClosed.complete(Unit)
                }
            } catch (error: Throwable) {
                peerClosed.completeExceptionally(error)
            }
        }

        fun assertPeerClosed() {
            peerClosed.get(3, TimeUnit.SECONDS)
        }

        override fun close() {
            socket.get()?.close()
            server.close()
            worker.join(1_000)
            assertFalse(worker.isAlive, "Loopback server did not stop")
        }
    }
}
