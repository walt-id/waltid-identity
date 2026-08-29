package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.proximity.ImmutableBytes
import id.walt.mdoc.proximity.ProximityCloseReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidNfcSessionRegistryTest {
    @After
    fun cleanRegistry() = runBlocking {
        AndroidNfcSessionRegistry.resetForTest()
    }

    @Test
    fun routesAndClosesOneGenerationExactlyOnce() = runBlocking {
        val router = RecordingRouter()
        val generation = AndroidNfcSessionRegistry.arm(router, Job())
        val session = requireNotNull(AndroidNfcSessionRegistry.current())

        val response = session.process(byteArrayOf(0x00, 0xa4.toByte()))
        AndroidNfcSessionRegistry.disarm(generation, ProximityCloseReason.COMPLETED)
        AndroidNfcSessionRegistry.disarm(generation, ProximityCloseReason.CANCELLED)

        assertContentEquals(byteArrayOf(0x90.toByte(), 0x00), response.copy())
        assertEquals(listOf(listOf(0x00, 0xa4.toByte())), router.commands.map(ByteArray::toList))
        assertEquals(listOf(ProximityCloseReason.COMPLETED), router.closeReasons)
        assertNull(AndroidNfcSessionRegistry.current())
    }

    @Test
    fun rejectsConcurrentGenerationWithoutLeakingParentCallback() = runBlocking {
        val firstRouter = RecordingRouter()
        val firstGeneration = AndroidNfcSessionRegistry.arm(firstRouter, Job())
        val rejectedParent = Job()
        val rejectedRouter = RecordingRouter()

        assertFailsWith<IllegalStateException> {
            AndroidNfcSessionRegistry.arm(rejectedRouter, rejectedParent)
        }
        rejectedParent.cancelAndJoin()
        yield()

        assertTrue(AndroidNfcSessionRegistry.isCurrent(firstGeneration))
        assertTrue(rejectedRouter.closeReasons.isEmpty())
    }

    @Test
    fun refusesAlreadyCancelledParentAndDeactivatesRouter() = runBlocking {
        val parent = Job().also { it.cancel() }
        val router = RecordingRouter()

        assertFailsWith<kotlinx.coroutines.CancellationException> {
            AndroidNfcSessionRegistry.arm(router, parent)
        }

        assertEquals(listOf(ProximityCloseReason.CANCELLED), router.closeReasons)
        assertNull(AndroidNfcSessionRegistry.current())
    }

    @Test
    fun parentCancellationDisarmsCurrentGeneration() = runBlocking {
        val parent = Job()
        val router = RecordingRouter()
        AndroidNfcSessionRegistry.arm(router, parent)

        parent.cancelAndJoin()
        eventually { AndroidNfcSessionRegistry.current() == null }

        assertEquals(listOf(ProximityCloseReason.CANCELLED), router.closeReasons)
    }

    @Test
    fun requestedDisarmInvalidatesGenerationBeforeAsynchronousCleanup() = runBlocking {
        val router = RecordingRouter()
        val generation = AndroidNfcSessionRegistry.arm(router, Job())
        val session = requireNotNull(AndroidNfcSessionRegistry.current())

        AndroidNfcSessionRegistry.requestDisarm(
            generation,
            ProximityCloseReason.PEER_DISCONNECTED,
        )

        assertNull(AndroidNfcSessionRegistry.current())
        assertFailsWith<IllegalStateException> {
            session.process(byteArrayOf(0x00))
        }
        eventually {
            router.closeReasons == listOf(ProximityCloseReason.PEER_DISCONNECTED)
        }
    }

    @Test
    fun staleDisarmCannotCloseNewGeneration() = runBlocking {
        val firstRouter = RecordingRouter()
        val first = AndroidNfcSessionRegistry.arm(firstRouter, Job())
        AndroidNfcSessionRegistry.disarm(first, ProximityCloseReason.COMPLETED)
        val secondRouter = RecordingRouter()
        val second = AndroidNfcSessionRegistry.arm(secondRouter, Job())

        AndroidNfcSessionRegistry.disarm(first, ProximityCloseReason.PEER_DISCONNECTED)

        assertTrue(AndroidNfcSessionRegistry.isCurrent(second))
        assertTrue(secondRouter.closeReasons.isEmpty())
    }

    @Test
    fun deactivationReachesTheRouterWhileApduProcessingIsInFlight() = runBlocking {
        val router = BlockingRouter()
        val generation = AndroidNfcSessionRegistry.arm(router, Job())
        val session = requireNotNull(AndroidNfcSessionRegistry.current())
        val processing = async { session.process(byteArrayOf(1)) }
        router.firstEntered.await()

        val closing = async {
            AndroidNfcSessionRegistry.disarm(generation, ProximityCloseReason.PEER_DISCONNECTED)
        }
        closing.await()
        assertEquals(listOf(ProximityCloseReason.PEER_DISCONNECTED), router.closeReasons)
        assertTrue(processing.isActive)
        router.releaseFirst.complete(Unit)
        processing.await()
        Unit
    }

    private suspend fun eventually(condition: () -> Boolean) {
        repeat(100) {
            if (condition()) return
            kotlinx.coroutines.delay(10)
        }
        assertTrue(condition(), "Condition was not satisfied before timeout")
    }

    private open class RecordingRouter : AndroidNfcSessionRegistry.Router {
        val commands = mutableListOf<ByteArray>()
        val closeReasons = mutableListOf<ProximityCloseReason>()

        override suspend fun process(command: ByteArray): ImmutableBytes {
            commands += command.copyOf()
            return ImmutableBytes.of(byteArrayOf(0x90.toByte(), 0x00))
        }

        override suspend fun deactivate(reason: ProximityCloseReason) {
            closeReasons += reason
        }
    }

    private class BlockingRouter : RecordingRouter() {
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        override suspend fun process(command: ByteArray): ImmutableBytes {
            if (command.contentEquals(byteArrayOf(1))) {
                firstEntered.complete(Unit)
                releaseFirst.await()
            }
            return super.process(command)
        }
    }
}
