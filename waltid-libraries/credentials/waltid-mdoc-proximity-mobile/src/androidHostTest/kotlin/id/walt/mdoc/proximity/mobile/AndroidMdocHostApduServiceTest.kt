package id.walt.mdoc.proximity.mobile

import android.nfc.cardemulation.HostApduService
import id.walt.mdoc.proximity.ImmutableBytes
import id.walt.mdoc.proximity.ProximityCloseReason
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidMdocHostApduServiceTest {
    @After
    fun cleanRegistry() = runBlocking {
        AndroidNfcSessionRegistry.resetForTest()
    }

    @Test
    fun rejectsNullAndUnarmedCommandsSynchronously() {
        val service = service()

        assertContentEquals(byteArrayOf(0x67, 0x00), service.processCommandApdu(null, null))
        assertContentEquals(
            byteArrayOf(0x69, 0x85.toByte()),
            service.processCommandApdu(byteArrayOf(0x00, 0xa4.toByte()), null),
        )
        assertTrue(service.responses.isEmpty())
    }

    @Test
    fun returnsImmediatelyAndSendsExactlyOneAsynchronousResponse() = runBlocking {
        val service = service()
        val router = BlockingRouter()
        val generation = AndroidNfcSessionRegistry.arm(router, Job())

        val immediate = service.processCommandApdu(byteArrayOf(0x00, 0xa4.toByte()), null)

        assertNull(immediate)
        withTimeout(TEST_TIMEOUT_MILLIS) { router.entered.await() }
        assertTrue(service.responses.isEmpty())
        router.release.complete(Unit)
        withTimeout(TEST_TIMEOUT_MILLIS) { service.responded.await() }

        assertEquals(1, service.responses.size)
        assertContentEquals(byteArrayOf(0x90.toByte(), 0x00), service.responses.single())
        AndroidNfcSessionRegistry.disarm(generation, ProximityCloseReason.COMPLETED)
    }

    @Test
    fun preservesApduArrivalOrderThroughOneBoundedAsynchronousWorker() = runBlocking {
        val service = service()
        val router = OrderedBlockingRouter()
        val generation = AndroidNfcSessionRegistry.arm(router, Job())

        assertNull(service.processCommandApdu(byteArrayOf(1), null))
        withTimeout(TEST_TIMEOUT_MILLIS) { router.firstEntered.await() }
        assertNull(service.processCommandApdu(byteArrayOf(2), null))
        router.releaseFirst.complete(Unit)

        eventually { service.responses.size == 2 }
        assertEquals(listOf(1, 2), router.commands)
        assertContentEquals(byteArrayOf(1, 0x90.toByte(), 0x00), service.responses[0])
        assertContentEquals(byteArrayOf(2, 0x90.toByte(), 0x00), service.responses[1])
        AndroidNfcSessionRegistry.disarm(generation, ProximityCloseReason.COMPLETED)
    }

    @Test
    fun fieldLossDropsAnInFlightResponseAndDeactivatesTheGeneration() = runBlocking {
        val service = service()
        val router = BlockingRouter()
        AndroidNfcSessionRegistry.arm(router, Job())
        service.processCommandApdu(byteArrayOf(0x00, 0xa4.toByte()), null)
        withTimeout(TEST_TIMEOUT_MILLIS) { router.entered.await() }

        service.onDeactivated(HostApduService.DEACTIVATION_LINK_LOSS)

        assertNull(AndroidNfcSessionRegistry.current())
        eventually { router.closeReasons == listOf(ProximityCloseReason.PEER_DISCONNECTED) }
        router.release.complete(Unit)
        assertTrue(service.responses.isEmpty())
    }

    @Test
    fun commandCancellationDoesNotKillTheServiceWorkerForTheNextGeneration() = runBlocking {
        val service = service()
        val cancelledGeneration = AndroidNfcSessionRegistry.arm(CancellingRouter(), Job())

        assertNull(service.processCommandApdu(byteArrayOf(1), null))
        eventually { service.responses.size == 1 }
        assertContentEquals(byteArrayOf(0x6f, 0x00), service.responses.single())
        AndroidNfcSessionRegistry.disarm(cancelledGeneration, ProximityCloseReason.CANCELLED)

        val nextGeneration = AndroidNfcSessionRegistry.arm(RecordingRouter(), Job())
        service.onDeactivated(HostApduService.DEACTIVATION_LINK_LOSS)
        assertTrue(AndroidNfcSessionRegistry.isCurrent(nextGeneration))
        assertNull(service.processCommandApdu(byteArrayOf(2), null))
        eventually { service.responses.size == 2 }
        assertContentEquals(byteArrayOf(0x90.toByte(), 0x00), service.responses.last())
        AndroidNfcSessionRegistry.disarm(nextGeneration, ProximityCloseReason.COMPLETED)
    }

    @Test
    fun lateFieldLossCannotCloseANewerGeneration() = runBlocking {
        val service = service()
        val first = AndroidNfcSessionRegistry.arm(RecordingRouter(), Job())

        assertNull(service.processCommandApdu(byteArrayOf(1), null))
        eventually { service.responses.size == 1 }
        AndroidNfcSessionRegistry.disarm(first, ProximityCloseReason.COMPLETED)

        val secondRouter = RecordingRouter()
        val second = AndroidNfcSessionRegistry.arm(secondRouter, Job())
        assertContentEquals(
            byteArrayOf(0x69, 0x85.toByte()),
            service.processCommandApdu(byteArrayOf(2), null),
        )

        service.onDeactivated(HostApduService.DEACTIVATION_LINK_LOSS)

        assertTrue(AndroidNfcSessionRegistry.isCurrent(second))
        assertTrue(secondRouter.closeReasons.isEmpty())
        assertNull(service.processCommandApdu(byteArrayOf(3), null))
        eventually { service.responses.size == 2 }
        AndroidNfcSessionRegistry.disarm(second, ProximityCloseReason.COMPLETED)
    }

    @Test
    fun serviceDestructionReleasesTheActiveGeneration() = runBlocking {
        val service = service()
        val router = RecordingRouter()
        AndroidNfcSessionRegistry.arm(router, Job())

        service.onDestroy()

        assertNull(AndroidNfcSessionRegistry.current())
        eventually { router.closeReasons == listOf(ProximityCloseReason.PLATFORM_UNAVAILABLE) }
    }

    private fun service(): RecordingMdocHostApduService =
        Robolectric.buildService(RecordingMdocHostApduService::class.java).create().get()

    private suspend fun eventually(condition: () -> Boolean) {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            while (!condition()) kotlinx.coroutines.yield()
        }
    }

    private open class RecordingRouter : AndroidNfcSessionRegistry.Router {
        val closeReasons = CopyOnWriteArrayList<ProximityCloseReason>()

        override suspend fun process(command: ByteArray): ImmutableBytes =
            ImmutableBytes.of(byteArrayOf(0x90.toByte(), 0x00))

        override suspend fun deactivate(reason: ProximityCloseReason) {
            closeReasons += reason
        }
    }

    private class BlockingRouter : RecordingRouter() {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        override suspend fun process(command: ByteArray): ImmutableBytes {
            entered.complete(Unit)
            release.await()
            return super.process(command)
        }
    }

    private class CancellingRouter : RecordingRouter() {
        override suspend fun process(command: ByteArray): ImmutableBytes =
            throw CancellationException("Current NFC command was cancelled")
    }

    private class OrderedBlockingRouter : AndroidNfcSessionRegistry.Router {
        val commands = CopyOnWriteArrayList<Int>()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()

        override suspend fun process(command: ByteArray): ImmutableBytes {
            val value = command.single().toInt()
            commands += value
            if (value == 1) {
                firstEntered.complete(Unit)
                releaseFirst.await()
            }
            return ImmutableBytes.of(byteArrayOf(value.toByte(), 0x90.toByte(), 0x00))
        }

        override suspend fun deactivate(reason: ProximityCloseReason) = Unit
    }

    private companion object {
        const val TEST_TIMEOUT_MILLIS = 2_000L
    }
}

class RecordingMdocHostApduService : AndroidMdocHostApduService() {
    val responses = CopyOnWriteArrayList<ByteArray>()
    val responded = CompletableDeferred<Unit>()

    override fun sendResponse(response: ByteArray) {
        responses += response.copyOf()
        responded.complete(Unit)
    }
}
