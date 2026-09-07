package id.walt.mdoc.proximity.mobile

import java.io.Closeable
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SocketCancellationTest {
    @Test fun cancellingBlockedOperationsClosesBeforeJoiningWorker() = runBlocking {
        for (operation in listOf("connect", "read", "write")) {
            val native = BlockingResource()
            val socket = BlockingSocket(native)
            val work = launch { socket.run { it.block(operation) } }
            withContext(Dispatchers.IO) { assertTrue(native.entered.await(5, TimeUnit.SECONDS)) }
            withTimeout(5_000) { work.cancelAndJoin() }
            socket.close()
            assertEquals(1, native.closes.get(), operation)
        }
    }

    @Test fun deadlineClosesBlockedOperationBeforeReturning() = runBlocking {
        val native = BlockingResource()
        val socket = BlockingSocket(native)
        assertFailsWith<TimeoutCancellationException> {
            withTimeout(100) { socket.run { it.block("deadline") } }
        }
        assertEquals(1, native.closes.get())
    }

    @Test fun cancellationBeforeWorkerStartsPreventsOperation() = runTest {
        val native = BlockingResource()
        val socket = BlockingSocket(native)
        val work = launch(start = CoroutineStart.UNDISPATCHED) {
            currentCoroutineContext().cancel()
            socket.run { fail("Cancelled operation must not run") }
        }
        work.join()
        socket.close()
        assertEquals(1, native.closes.get())
    }

    @Test fun cancellationBetweenNativeSuccessAndResumeDisposesAcceptedResource() = runTest {
        val listener = BlockingResource()
        val accepted = BlockingResource()
        val completed = CountDownLatch(1)
        val owner = BlockingSocket(listener)
        val work = launch {
            owner.run(disposeResult = { it.close() }) { accepted.also { completed.countDown() } }
            fail("Cancelled result must not be delivered")
        }
        runCurrent()
        assertTrue(completed.await(5, TimeUnit.SECONDS))
        work.cancel()
        work.join()
        owner.close()
        assertEquals(1, listener.closes.get())
        assertEquals(1, accepted.closes.get())
    }

    @Test fun failedOperationClosesOwnerAndFreshOwnerCanSucceed() = runBlocking {
        val native = BlockingResource()
        val socket = BlockingSocket(native)
        assertFailsWith<IOException> { socket.run<Unit> { throw IOException("failed setup") } }
        socket.close()
        assertEquals(1, native.closes.get())
        val fresh = BlockingResource()
        BlockingSocket(fresh).use { assertEquals(42, it.run { 42 }) }
        assertEquals(1, fresh.closes.get())
    }

    @Test fun realSocketReadAndAcceptUnblockOnCancellation() = runBlocking {
        val listening = BlockingSocket(ServerSocket(0))
        val pendingAccept = launch { listening.run<Socket>(disposeResult = { it.close() }) { it.accept() } }
        yield()
        withTimeout(5_000) { pendingAccept.cancelAndJoin() }
        assertTrue(listening.socket.isClosed)
        ServerSocket(0).use { server ->
            Socket("127.0.0.1", server.localPort).use { client ->
                val accepted = BlockingSocket(server.accept())
                val entered = CountDownLatch(1)
                val read = launch { accepted.run { entered.countDown(); it.getInputStream().read() } }
                withContext(Dispatchers.IO) { assertTrue(entered.await(5, TimeUnit.SECONDS)) }
                withTimeout(5_000) { read.cancelAndJoin() }
                assertTrue(accepted.socket.isClosed)
                assertFalse(client.isClosed)
            }
        }
    }

    private class BlockingResource : Closeable {
        val entered = CountDownLatch(1)
        val closes = AtomicInteger()
        private val closed = CountDownLatch(1)
        fun block(operation: String) {
            entered.countDown()
            check(closed.await(10, TimeUnit.SECONDS)) { "$operation was not unblocked by close" }
            throw IOException("closed")
        }
        override fun close() { closes.incrementAndGet(); closed.countDown() }
    }
}
