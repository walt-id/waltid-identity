@file:OptIn(
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
    kotlinx.serialization.ExperimentalSerializationApi::class,
)

package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.objects.engagement.BleCentralMode
import id.walt.mdoc.objects.engagement.DeviceRetrievalMethod
import id.walt.mdoc.proximity.FakeProximityLoopback
import id.walt.mdoc.proximity.ImmutableBytes
import id.walt.mdoc.proximity.PreparedTransport
import id.walt.mdoc.proximity.ProximityCloseReason
import id.walt.mdoc.proximity.ProximityConnection
import id.walt.mdoc.proximity.ProximityException
import id.walt.mdoc.proximity.ProximityTransportKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NfcV2HybridProximityConnectionTest {
    @Test
    fun `first bearer wins and an exact duplicate is conveyed only once`() = runTest {
        val nfc = NfcApduProximityConnection()
        val alternate = FakeProximityLoopback.create()
        val prepared = DeferredPreparedTransport().also { it.connect(alternate.holder) }
        val connection = hybrid(nfc, prepared)
        runCurrent()

        val first = byteArrayOf(1, 2, 3)
        val nfcExchange = async { exchange(nfc, 0u, first) }
        assertContentEquals(first, connection.receive()!!.copy())

        alternate.reader.send(ImmutableBytes.of(first))
        val next = async { connection.receive() }
        runCurrent()
        assertTrue(next.isActive)

        val second = byteArrayOf(4, 5, 6)
        alternate.reader.send(ImmutableBytes.of(second))
        assertContentEquals(second, next.await()!!.copy())

        val response = ImmutableBytes.of(byteArrayOf(7, 8))
        connection.send(response)
        assertEquals(response, nfcExchange.await())
        assertEquals(response, alternate.reader.receive())
        connection.close(ProximityCloseReason.COMPLETED)
    }

    @Test
    fun `divergent duplicates fail before reaching the session engine`() = runTest {
        val nfc = NfcApduProximityConnection()
        val alternate = FakeProximityLoopback.create()
        val prepared = DeferredPreparedTransport().also { it.connect(alternate.holder) }
        val connection = hybrid(nfc, prepared)
        runCurrent()

        val nfcExchange = async { exchange(nfc, 0u, byteArrayOf(1)) }
        assertContentEquals(byteArrayOf(1), connection.receive()!!.copy())
        connection.send(ImmutableBytes.of(byteArrayOf(9)))
        assertContentEquals(byteArrayOf(9), nfcExchange.await().copy())
        alternate.reader.send(ImmutableBytes.of(byteArrayOf(2)))

        val failure = assertFailsWith<ProximityException> { connection.receive() }
        assertEquals("nfc_v2_hybrid_duplicate_mismatch", failure.error.code)
        connection.close(ProximityCloseReason.PROTOCOL_ERROR)
    }

    @Test
    fun `alternate bearer can connect after NFC field loss and receive the backlog`() = runTest {
        val nfc = NfcApduProximityConnection()
        val alternate = FakeProximityLoopback.create()
        val prepared = DeferredPreparedTransport()
        val connection = hybrid(nfc, prepared)
        val incoming = async { connection.receive() }

        nfc.close(ProximityCloseReason.PEER_DISCONNECTED)
        runCurrent()
        assertTrue(incoming.isActive)

        prepared.connect(alternate.holder)
        runCurrent()
        val request = ImmutableBytes.of(byteArrayOf(3, 4))
        alternate.reader.send(request)
        assertEquals(request, incoming.await())

        val response = ImmutableBytes.of(byteArrayOf(5, 6))
        connection.send(response)
        assertEquals(response, alternate.reader.receive())
        connection.close(ProximityCloseReason.COMPLETED)
    }

    @Test
    fun `a stalled alternate send does not delay a successful NFC response`() = runTest {
        val nfc = NfcApduProximityConnection()
        val alternate = FakeProximityLoopback.create()
        val releaseAlternateSend = CompletableDeferred<Unit>()
        val prepared = DeferredPreparedTransport().also {
            it.connect(BlockingSendConnection(alternate.holder, releaseAlternateSend))
        }
        val connection = hybrid(nfc, prepared)
        runCurrent()

        val exchange = async { exchange(nfc, 0u, byteArrayOf(1)) }
        assertContentEquals(byteArrayOf(1), connection.receive()!!.copy())

        val response = ImmutableBytes.of(byteArrayOf(2))
        val send = async { connection.send(response) }
        runCurrent()

        assertTrue(send.isCompleted)
        send.await()
        assertEquals(response, exchange.await())

        releaseAlternateSend.complete(Unit)
        runCurrent()
        assertEquals(response, alternate.reader.receive())
        connection.close(ProximityCloseReason.COMPLETED)
    }

    @Test
    fun `alternate provider cancellation does not cancel a live NFC path`() = runTest {
        val nfc = NfcApduProximityConnection()
        val connection = hybrid(
            nfc,
            FailingPreparedTransport(CancellationException("alternate provider stopped")),
        )
        runCurrent()

        val request = byteArrayOf(1, 3, 5)
        val exchange = async { exchange(nfc, 0u, request) }
        assertContentEquals(request, connection.receive()!!.copy())
        val response = ImmutableBytes.of(byteArrayOf(2, 4, 6))
        connection.send(response)

        assertEquals(response, exchange.await())
        connection.close(ProximityCloseReason.COMPLETED)
    }

    @Test
    fun `failure is reported only after both hybrid bearers end`() = runTest {
        val nfc = NfcApduProximityConnection()
        val connection = hybrid(
            nfc,
            FailingPreparedTransport(CancellationException("alternate provider stopped")),
        )
        nfc.close(ProximityCloseReason.PEER_DISCONNECTED)
        runCurrent()

        val failure = assertFailsWith<ProximityException> { connection.receive() }
        assertEquals("nfc_v2_hybrid_receive_failed", failure.error.code)
        connection.close(ProximityCloseReason.PEER_DISCONNECTED)
    }

    @Test
    fun `orderly bearer completion remains terminal for repeated receives`() = runTest {
        val nfc = NfcApduProximityConnection()
        val alternate = FakeProximityLoopback.create()
        val prepared = DeferredPreparedTransport().also { it.connect(alternate.holder) }
        val connection = hybrid(nfc, prepared)
        runCurrent()

        nfc.close(ProximityCloseReason.PEER_DISCONNECTED)
        alternate.reader.close(ProximityCloseReason.PEER_DISCONNECTED)
        runCurrent()

        assertEquals(null, connection.receive())
        assertEquals(null, connection.receive())
        connection.close(ProximityCloseReason.PEER_DISCONNECTED)
    }

    @Test
    fun `message limit applies independently in both directions`() = runTest {
        val nfc = NfcApduProximityConnection()
        val connection = NfcV2HybridProximityConnection(
            nfc = nfc,
            alternate = FailingPreparedTransport(CancellationException("no alternate")),
            sessionScope = this,
            maximumMessagesPerDirection = 1,
        )
        runCurrent()

        val first = async { exchange(nfc, 0u, byteArrayOf(1)) }
        assertContentEquals(byteArrayOf(1), connection.receive()!!.copy())
        connection.send(ImmutableBytes.of(byteArrayOf(2)))
        assertContentEquals(byteArrayOf(2), first.await().copy())

        val outgoingFailure = assertFailsWith<ProximityException> {
            connection.send(ImmutableBytes.of(byteArrayOf(3)))
        }
        assertEquals("nfc_v2_hybrid_message_limit", outgoingFailure.error.code)

        val second = async { runCatching { exchange(nfc, 1u, byteArrayOf(4)) } }
        val incomingFailure = assertFailsWith<ProximityException> { connection.receive() }
        assertEquals("nfc_v2_hybrid_message_limit", incomingFailure.error.code)
        connection.close(ProximityCloseReason.PROTOCOL_ERROR)
        assertTrue(second.await().isFailure)
    }

    @Test
    fun `close releases platform IO before joining hybrid workers`() = runTest {
        val prepared = CloseUnblocksReceivePreparedTransport()
        val connection = hybrid(NfcApduProximityConnection(), prepared)
        runCurrent()

        val closing = async { connection.close(ProximityCloseReason.CANCELLED) }
        runCurrent()
        val completedBeforeExternalRelease = closing.isCompleted
        if (!completedBeforeExternalRelease) prepared.forceRelease()
        closing.await()

        assertTrue(completedBeforeExternalRelease)
        assertEquals(1, prepared.closeCalls)
        assertEquals(1, prepared.connection.closeCalls)

        connection.close(ProximityCloseReason.CANCELLED)
        assertEquals(1, prepared.closeCalls)
        assertEquals(1, prepared.connection.closeCalls)
    }

    private fun CoroutineScope.hybrid(
        nfc: NfcApduProximityConnection,
        alternate: PreparedTransport,
    ): NfcV2HybridProximityConnection = NfcV2HybridProximityConnection(
        nfc = nfc,
        alternate = alternate,
        sessionScope = this,
        maximumMessagesPerDirection = 8,
    )

    private suspend fun exchange(
        connection: NfcApduProximityConnection,
        identifier: ULong,
        bytes: ByteArray,
    ): ImmutableBytes = connection.exchange(
        identifier = identifier,
        message = ImmutableBytes.of(bytes),
        cancel = {},
        complete = { _, response -> response },
    )

    private class DeferredPreparedTransport : PreparedTransport {
        private val connection = CompletableDeferred<ProximityConnection>()
        override val kind: ProximityTransportKind = ProximityTransportKind.FAKE
        override val connectionMethod: DeviceRetrievalMethod = DeviceRetrievalMethod.Ble(
            centralMode = BleCentralMode(ByteArray(16)),
        )

        fun connect(value: ProximityConnection) {
            check(connection.complete(value))
        }

        override suspend fun awaitConnection(): ProximityConnection = connection.await()

        override suspend fun close(reason: ProximityCloseReason) {
            if (connection.isCompleted) connection.await().close(reason)
            else connection.cancel()
        }
    }

    private class FailingPreparedTransport(
        private val failure: CancellationException,
    ) : PreparedTransport {
        override val kind: ProximityTransportKind = ProximityTransportKind.FAKE
        override val connectionMethod: DeviceRetrievalMethod = DeviceRetrievalMethod.Ble(
            centralMode = BleCentralMode(ByteArray(16)),
        )

        override suspend fun awaitConnection(): ProximityConnection = throw failure

        override suspend fun close(reason: ProximityCloseReason) = Unit
    }

    private class BlockingSendConnection(
        private val delegate: ProximityConnection,
        private val release: CompletableDeferred<Unit>,
    ) : ProximityConnection {
        override val kind: ProximityTransportKind = delegate.kind

        override suspend fun send(message: ImmutableBytes) {
            release.await()
            delegate.send(message)
        }

        override suspend fun receive(): ImmutableBytes? = delegate.receive()

        override suspend fun close(reason: ProximityCloseReason) = delegate.close(reason)
    }

    private class CloseUnblocksReceivePreparedTransport : PreparedTransport {
        private val released = CompletableDeferred<Unit>()
        val connection = CloseUnblocksReceiveConnection(released)
        var closeCalls = 0
            private set
        private var closed = false

        override val kind: ProximityTransportKind = ProximityTransportKind.FAKE
        override val connectionMethod: DeviceRetrievalMethod = DeviceRetrievalMethod.Ble(
            centralMode = BleCentralMode(ByteArray(16)),
        )

        override suspend fun awaitConnection(): ProximityConnection = connection

        override suspend fun close(reason: ProximityCloseReason) {
            if (closed) return
            closed = true
            closeCalls++
            connection.close(reason)
        }

        fun forceRelease() {
            released.complete(Unit)
        }
    }

    private class CloseUnblocksReceiveConnection(
        private val released: CompletableDeferred<Unit>,
    ) : ProximityConnection {
        override val kind: ProximityTransportKind = ProximityTransportKind.FAKE
        var closeCalls = 0
            private set
        private var closed = false

        override suspend fun send(message: ImmutableBytes) = Unit

        override suspend fun receive(): ImmutableBytes? = withContext(NonCancellable) {
            released.await()
            null
        }

        override suspend fun close(reason: ProximityCloseReason) {
            if (closed) return
            closed = true
            closeCalls++
            released.complete(Unit)
        }
    }
}
