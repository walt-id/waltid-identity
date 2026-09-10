package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.proximity.ProximityCloseReason
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class AndroidWifiAwareRawConnectionTest {
    @Test fun platformClosureEndsPassiveHttpObservationWithoutReadingTheSocket() = runBlocking {
        val socket = BlockedSocket()
        val raw = AndroidWifiAwareRawConnection(BlockingSocket(socket))
        val connection = WifiAwareHttpConnection(raw, 16)
        val cancelled = async(start = CoroutineStart.UNDISPATCHED) { connection.awaitClosed() }
        val observer = async(start = CoroutineStart.UNDISPATCHED) { connection.awaitClosed() }
        cancelled.cancelAndJoin()
        assertEquals(1L, socket.entered.count)
        assertEquals(0, socket.closes.get())
        assertTrue(observer.isActive)
        raw.close(ProximityCloseReason.PEER_DISCONNECTED)
        connection.close(ProximityCloseReason.COMPLETED)
        assertEquals(ProximityCloseReason.PEER_DISCONNECTED, withTimeout(5_000) { observer.await() })
        assertEquals(ProximityCloseReason.PEER_DISCONNECTED, connection.awaitClosed())
        assertEquals(1, socket.closes.get())
    }

    @Test fun cancellationClosesTheActualPendingReadOrWrite() = runBlocking {
        for (writing in listOf(false, true)) {
            val socket = BlockedSocket()
            val connection = AndroidWifiAwareRawConnection(BlockingSocket(socket))
            val pending = launch {
                if (writing) connection.write(byteArrayOf(1)) else connection.read(16)
            }
            withContext(Dispatchers.IO) { assertTrue(socket.entered.await(5, TimeUnit.SECONDS)) }
            withTimeout(5_000) { pending.cancelAndJoin() }
            connection.close(ProximityCloseReason.CANCELLED)
            assertEquals(1, socket.closes.get())
        }
        // A cancelled transaction does not poison a fresh stream.
        ServerSocket(0).use { server ->
            Socket("127.0.0.1", server.localPort).use { client ->
                val connection = AndroidWifiAwareRawConnection(BlockingSocket(server.accept()))
                client.getOutputStream().write(byteArrayOf(7))
                assertContentEquals(byteArrayOf(7), connection.read(8))
                connection.write(byteArrayOf(9))
                assertEquals(9, client.getInputStream().read())
                connection.close(ProximityCloseReason.COMPLETED)
            }
        }
    }

    private class BlockedSocket : Socket() {
        val entered = CountDownLatch(1)
        val closes = AtomicInteger()
        private val released = CountDownLatch(1)
        private fun block(): Nothing {
            entered.countDown()
            check(released.await(10, TimeUnit.SECONDS)) { "Socket was not closed" }
            throw IOException("closed")
        }
        override fun getInputStream(): InputStream = object : InputStream() {
            override fun read(): Int = block()
        }
        override fun getOutputStream(): OutputStream = object : OutputStream() {
            override fun write(value: Int) = block()
        }
        override fun close() { closes.incrementAndGet(); released.countDown() }
    }
}
