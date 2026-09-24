package id.walt.itb

import io.ktor.http.Url
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.io.EOFException
import kotlin.test.*

class ItbDeviceWireTest {
    private val token = "a".repeat(64)

    @Test
    fun `only the approved origin is accepted with or without its root slash`() {
        listOf("https://dev-i4mlab.aegean.gr", "https://dev-i4mlab.aegean.gr/").forEach {
            assertEquals("dev-i4mlab.aegean.gr", ItbDeviceWire.approvedOrigin(it).host)
        }
        listOf("http://dev-i4mlab.aegean.gr", "https://other.example/", "https://dev-i4mlab.aegean.gr:444/",
            "https://user@dev-i4mlab.aegean.gr/", "https://dev-i4mlab.aegean.gr/path",
            "https://dev-i4mlab.aegean.gr/?query=1", "https://dev-i4mlab.aegean.gr/#fragment").forEach {
            assertFailsWith<IllegalArgumentException> { ItbDeviceWire.approvedOrigin(it) }
        }
    }

    @Test
    fun `device authentication requires the exact token and protocol`() {
        fun init(secret: String = token, version: Int = 1) = buildJsonObject {
            put("token", secret); put("version", version)
        }
        ItbDeviceWire.authenticate(init(), token)
        assertFailsWith<IllegalArgumentException> { ItbDeviceWire.authenticate(init("b".repeat(64)), token) }
        assertFailsWith<IllegalArgumentException> { ItbDeviceWire.authenticate(init(version = 2), token) }
        assertFailsWith<IllegalArgumentException> { ItbDeviceWire.authenticate(init(), "short") }
    }

    @Test
    fun `all operation types preserve their exact correlated inputs`() {
        val inputs = listOf(
            ItbWalletInteraction.Offer(Url("openid-credential-offer://?credential_offer_uri=https%3A%2F%2Fissuer.example%2Foffer"), "123456"),
            ItbWalletInteraction.Presentation(Url("openid4vp://?request_uri=https%3A%2F%2Fverifier.example%2Frequest")),
            ItbWalletInteraction.DigitalCredentials(
                Url("https://verifier.example/descriptor"), "session", "profile", "openid4vp-v1-signed",
                ItbWalletInteraction.Payment("sca-iban", "Merchant", "payee", "EUR", "12.95", "transaction"),
            ),
        )
        inputs.forEach { input ->
            val encoded = ItbDeviceWire.encode(input)
            assertEquals(encoded, ItbDeviceWire.encode(ItbDeviceWire.decode(encoded)))
        }
        assertFails { ItbDeviceWire.decode(buildJsonObject { put("kind", "sign") }) }
    }

    @Test
    fun `frame round trip and oversized frame rejection`() = withSockets { client, server ->
        val message = buildJsonObject { put("value", "bounded fixture") }
        ItbDeviceWire.write(client, message)
        assertEquals(message, ItbDeviceWire.read(server))
        client.getOutputStream().write(ByteBuffer.allocate(4).putInt(1024 * 1024 + 1).array())
        assertFailsWith<IllegalArgumentException> { ItbDeviceWire.read(server) }
    }

    @Test
    fun `disconnect cancels a pending wallet operation`() = withSockets { client, server ->
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val job = launch {
            ItbDeviceWire.serve(server) {
                started.complete(Unit)
                try { awaitCancellation() } finally { cancelled.complete(Unit) }
            }
        }
        ItbDeviceWire.write(client, operation(1))
        started.await()
        client.close()
        cancelled.await()
        job.join()
        assertTrue(job.isCancelled)
    }

    @Test
    fun `a completed operation cannot be replayed`() = withSockets { client, server ->
        var calls = 0
        val job = async {
            runCatching { ItbDeviceWire.serve(server) { calls++ } }
        }
        ItbDeviceWire.write(client, operation(1))
        assertTrue(ItbDeviceWire.read(client).getValue("success").jsonPrimitive.boolean)
        ItbDeviceWire.write(client, operation(1))
        assertIs<IllegalArgumentException>(job.await().exceptionOrNull())
        assertEquals(1, calls)
    }

    @Test
    fun `wallet failure returns no protocol data and permits the next case`() = withSockets { client, server ->
        var calls = 0
        val job = launch {
            ItbDeviceWire.serve(server) { if (++calls == 1) error("secret bearer URL must not be returned") }
        }
        ItbDeviceWire.write(client, operation(1))
        val failure = ItbDeviceWire.read(client)
        assertEquals(setOf("sequence", "success", "failure", "trace"), failure.keys)
        assertEquals("IllegalStateException", failure.getValue("failure").jsonPrimitive.content)
        assertFalse(failure.toString().contains("secret"))
        ItbDeviceWire.write(client, operation(2))
        assertTrue(ItbDeviceWire.read(client).getValue("success").jsonPrimitive.boolean)
        client.close(); job.join()
    }

    @Test
    fun `host rejects an uncorrelated result`() = runBlocking {
        withTimeout(5_000) {
            ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { listener ->
                val peer = launch(Dispatchers.IO) {
                    listener.accept().use { socket ->
                        ItbDeviceWire.authenticate(ItbDeviceWire.read(socket), token)
                        ItbDeviceWire.write(socket, ready())
                        ItbDeviceWire.read(socket)
                        ItbDeviceWire.write(socket, buildJsonObject { put("sequence", 2); put("success", true) })
                    }
                }
                ItbAndroidWalletDriver.connect(listener.localPort, token, Url("https://dev-i4mlab.aegean.gr"), "fixture", Dispatchers.IO).use {
                    assertFailsWith<IllegalStateException> {
                        it.execute(ItbWalletInteraction.Presentation(Url("openid4vp://?request=fixture")))
                    }
                }
                peer.join()
            }
        }
    }

    @Test
    fun `host cancellation closes the device channel`() = runBlocking {
        withTimeout(5_000) {
            ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { listener ->
                val received = CompletableDeferred<Unit>()
                val peer = async(Dispatchers.IO) {
                    listener.accept().use { socket ->
                        ItbDeviceWire.authenticate(ItbDeviceWire.read(socket), token)
                        ItbDeviceWire.write(socket, ready())
                        ItbDeviceWire.read(socket)
                        received.complete(Unit)
                        runCatching { ItbDeviceWire.read(socket) }.exceptionOrNull()
                    }
                }
                ItbAndroidWalletDriver.connect(listener.localPort, token, Url("https://dev-i4mlab.aegean.gr"), "fixture", Dispatchers.IO).use { driver ->
                    val pending = launch { driver.execute(ItbWalletInteraction.Presentation(Url("openid4vp://?request=fixture"))) }
                    received.await()
                    pending.cancelAndJoin()
                    assertIs<EOFException>(peer.await())
                }
            }
        }
    }

    @Test
    fun `timeout closes a channel while the peer stops reading`() = withSockets { client, server ->
        client.sendBufferSize = 4_096
        server.receiveBufferSize = 4_096
        val largeFrame = buildJsonObject { put("data", "x".repeat(900_000)) }
        assertFailsWith<TimeoutCancellationException> {
            ItbAndroidWalletDriver.withSocketDeadline(client, 200, Dispatchers.IO) {
                repeat(20) { ItbDeviceWire.write(client, largeFrame) }
            }
        }
        assertTrue(client.isClosed)
    }

    private fun ready() = buildJsonObject { put("ready", true); put("provider", "android-native-biometric") }

    private fun operation(sequence: Int) = buildJsonObject {
        put("sequence", sequence)
        put("interaction", ItbDeviceWire.encode(ItbWalletInteraction.Presentation(Url("openid4vp://?request=fixture"))))
    }

    private fun withSockets(block: suspend CoroutineScope.(Socket, Socket) -> Unit) = runBlocking {
        withTimeout(5_000) {
            ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { listener ->
                Socket("127.0.0.1", listener.localPort).use { client ->
                    listener.accept().use { server -> block(client, server) }
                }
            }
        }
    }
}
