@file:OptIn(
    kotlinx.serialization.ExperimentalSerializationApi::class,
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
)

package id.walt.mdoc.proximity

import id.walt.mdoc.objects.engagement.DeviceRetrievalMethod
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class TransportCoordinatorTest {
    private val method = DeviceRetrievalMethod.Nfc(256u, 256u)

    @Test
    fun `first successful transport wins and the loser is closed once`() = runTest {
        val winningConnection = FakeProximityLoopback.create().holder
        val failing = TrackingPrepared(ProximityTransportKind.BLE, failure = IllegalStateException("no peer"))
        val winning = TrackingPrepared(ProximityTransportKind.NFC, connection = winningConnection)

        val result = TransportCoordinator().awaitWinner(PreparedTransports(listOf(failing, winning), emptyMap()))

        assertSame(winning, result.prepared)
        assertSame(winningConnection, result.connection)
        assertEquals(listOf(ProximityCloseReason.LOST_RACE), failing.closeReasons)
        assertEquals(emptyList(), winning.closeReasons)
    }

    @Test
    fun `all connection failures close every prepared transport`() = runTest {
        val first = TrackingPrepared(ProximityTransportKind.BLE, failure = IllegalStateException("one"))
        val second = TrackingPrepared(ProximityTransportKind.NFC, failure = IllegalStateException("two"))

        assertFailsWith<ProximityException> {
            TransportCoordinator().awaitWinner(PreparedTransports(listOf(first, second), emptyMap()))
        }
        assertEquals(listOf(ProximityCloseReason.PEER_DISCONNECTED), first.closeReasons)
        assertEquals(listOf(ProximityCloseReason.PEER_DISCONNECTED), second.closeReasons)
    }

    @Test
    fun `cancelling connection selection closes every prepared transport exactly once`() = runTest {
        val first = TrackingPrepared(ProximityTransportKind.BLE, waitForever = true)
        val second = TrackingPrepared(ProximityTransportKind.NFC, waitForever = true)
        val selection = async {
            TransportCoordinator().awaitWinner(PreparedTransports(listOf(first, second), emptyMap()))
        }
        runCurrent()

        selection.cancelAndJoin()

        assertEquals(listOf(ProximityCloseReason.CANCELLED), first.closeReasons)
        assertEquals(listOf(ProximityCloseReason.CANCELLED), second.closeReasons)
    }

    @Test
    fun `cancelling preparation closes transports already prepared`() = runTest {
        val first = TrackingPrepared(ProximityTransportKind.BLE, waitForever = true)
        val firstProvider = provider(ProximityTransportKind.BLE) { first }
        val blockingProvider = provider(ProximityTransportKind.NFC) { awaitCancellation() }
        val preparation = async {
            TransportCoordinator().prepare(
                listOf(firstProvider, blockingProvider),
                EngagementContext(MdocProximityProfile.ISO_18013_5_ED2_DIS_2026, 1024, MdocEngagementMode.Qr),
                this,
            )
        }
        runCurrent()

        preparation.cancelAndJoin()

        assertEquals(listOf(ProximityCloseReason.CANCELLED), first.closeReasons)
    }

    @Test
    fun `prepared transport with a mismatched identifier is closed and rejected`() = runTest {
        val candidate = TrackingPrepared(
            kind = ProximityTransportKind.BLE,
            id = PreparedTransportId("unexpected"),
            waitForever = true,
        )

        assertFailsWith<IllegalArgumentException> {
            TransportCoordinator().prepare(
                listOf(provider(ProximityTransportKind.BLE) { candidate }),
                EngagementContext(MdocProximityProfile.ISO_18013_5_ED2_DIS_2026, 1024, MdocEngagementMode.Qr),
                this,
            )
        }

        assertEquals(listOf(ProximityCloseReason.CANCELLED), candidate.closeReasons)
    }

    @Test
    fun `a connection delivered after cancellation is discarded and closed`() = runTest {
        val release = CompletableDeferred<Unit>()
        val lateConnection = TrackingConnection()
        val prepared = object : PreparedTransport {
            override val kind = ProximityTransportKind.BLE
            override val connectionMethod = method
            val closeReasons = mutableListOf<ProximityCloseReason>()
            override suspend fun awaitConnection(): ProximityConnection = withContext(NonCancellable) {
                release.await()
                lateConnection
            }
            override suspend fun close(reason: ProximityCloseReason) { closeReasons += reason }
        }
        val selection = async {
            TransportCoordinator().awaitWinner(PreparedTransports(listOf(prepared), emptyMap()))
        }
        runCurrent()

        selection.cancel()
        release.complete(Unit)
        selection.join()

        assertEquals(listOf(ProximityCloseReason.LOST_RACE), lateConnection.closeReasons)
        assertEquals(listOf(ProximityCloseReason.CANCELLED), prepared.closeReasons)
    }

    @Test
    fun `an available but unselected transport is not prepared or advertised`() = runTest {
        val selected = TrackingPrepared(ProximityTransportKind.BLE, waitForever = true)
        var unselectedPrepareCalls = 0
        val prepared = TransportCoordinator().prepare(
            listOf(
                provider(ProximityTransportKind.BLE) { selected },
                provider(
                    ProximityTransportKind.NFC,
                    ProximityCapability(true, true, true, sessionSelected = false),
                ) {
                    unselectedPrepareCalls++
                    TrackingPrepared(ProximityTransportKind.NFC, waitForever = true)
                },
            ),
            EngagementContext(MdocProximityProfile.ISO_18013_5_ED2_DIS_2026, 1024, MdocEngagementMode.Qr),
            this,
        )

        assertEquals(listOf(selected), prepared.transports)
        assertEquals(0, unselectedPrepareCalls)
        assertNotNull(prepared.unavailable[ProximityTransportKind.NFC])
    }

    private fun provider(
        kind: ProximityTransportKind,
        capability: ProximityCapability = ProximityCapability(true, true, true, sessionSelected = true),
        prepare: suspend CoroutineScope.() -> PreparedTransport,
    ) = object : ProximityTransportProvider {
        override val kind = kind
        override suspend fun capability(context: EngagementContext) = capability
        override suspend fun prepare(context: EngagementContext, sessionScope: CoroutineScope): PreparedTransport =
            sessionScope.prepare()
    }

    private inner class TrackingPrepared(
        override val kind: ProximityTransportKind,
        override val id: PreparedTransportId = PreparedTransportId(kind.name),
        private val connection: ProximityConnection? = null,
        private val failure: Throwable? = null,
        private val waitForever: Boolean = false,
    ) : PreparedTransport {
        override val connectionMethod: DeviceRetrievalMethod = method
        val closeReasons = mutableListOf<ProximityCloseReason>()
        override suspend fun awaitConnection(): ProximityConnection = when {
            waitForever -> awaitCancellation()
            connection != null -> connection
            else -> throw requireNotNull(failure)
        }
        override suspend fun close(reason: ProximityCloseReason) { closeReasons += reason }
    }

    private class TrackingConnection : ProximityConnection {
        override val kind = ProximityTransportKind.FAKE
        val closeReasons = mutableListOf<ProximityCloseReason>()
        override suspend fun receive(): ImmutableBytes? = null
        override suspend fun send(message: ImmutableBytes) = Unit
        override suspend fun close(reason: ProximityCloseReason) { closeReasons += reason }
    }
}
