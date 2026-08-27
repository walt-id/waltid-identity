package id.walt.mdoc.proximity

import id.walt.mdoc.objects.engagement.DeviceRetrievalMethod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Deterministic full-duplex message pair used by common holder/reader contract tests and UI fixtures. */
class FakeProximityLoopback private constructor(
    val holder: FakeProximityConnection,
    val reader: FakeProximityConnection,
) {
    companion object {
        fun create(capacity: Int = Channel.UNLIMITED): FakeProximityLoopback {
            val holderInbound = Channel<ImmutableBytes>(capacity)
            val readerInbound = Channel<ImmutableBytes>(capacity)
            return FakeProximityLoopback(
                holder = FakeProximityConnection(holderInbound, readerInbound),
                reader = FakeProximityConnection(readerInbound, holderInbound),
            )
        }
    }
}

class FakeProximityConnection internal constructor(
    private val inbound: Channel<ImmutableBytes>,
    private val outbound: Channel<ImmutableBytes>,
) : ProximityConnection {
    private val stateMutex = Mutex()
    private val sendMutex = Mutex()
    private var terminal = false
    override val kind: ProximityTransportKind = ProximityTransportKind.FAKE

    override suspend fun receive(): ImmutableBytes? = inbound.receiveCatching().getOrNull()

    override suspend fun send(message: ImmutableBytes) = sendMutex.withLock {
        stateMutex.withLock { check(!terminal) { "Fake connection is closed" } }
        outbound.send(ImmutableBytes.of(message.copy()))
    }

    override suspend fun close(reason: ProximityCloseReason): Unit = stateMutex.withLock {
        if (terminal) return
        terminal = true
        inbound.close()
        outbound.close()
    }

}

class FakePreparedTransport(
    override val connectionMethod: DeviceRetrievalMethod,
    private val connection: ProximityConnection,
    override val sessionTranscriptFactory: SessionTranscriptFactory = QrSessionTranscriptFactory,
) : PreparedTransport {
    override val kind: ProximityTransportKind = ProximityTransportKind.FAKE
    private val mutex = Mutex()
    private var terminal = false
    override suspend fun awaitConnection(): ProximityConnection = mutex.withLock {
        check(!terminal) { "Fake prepared transport is closed" }
        connection
    }
    override suspend fun close(reason: ProximityCloseReason) {
        val closeConnection = mutex.withLock {
            if (terminal) false else true.also { terminal = true }
        }
        if (closeConnection) connection.close(reason)
    }
}

class FakeTransportProvider(
    private val method: DeviceRetrievalMethod,
    private val connection: ProximityConnection,
    private val availability: ProximityCapability = ProximityCapability(true, true, true, sessionSelected = true),
) : ProximityTransportProvider {
    override val kind: ProximityTransportKind = ProximityTransportKind.FAKE
    override suspend fun capability(context: EngagementContext): ProximityCapability = availability
    override suspend fun prepare(context: EngagementContext, sessionScope: CoroutineScope): PreparedTransport {
        check(availability.mayPrepare) { "Fake transport is unavailable" }
        return FakePreparedTransport(method, connection)
    }
}
