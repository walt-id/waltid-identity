package id.walt.mdoc.proximity

import id.walt.mdoc.objects.engagement.DeviceRetrievalMethod
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Deterministic full-duplex message pair used by common holder/reader contract tests. */
class FakeProximityLoopback private constructor(
    val holder: FakeProximityConnection,
    val reader: FakeProximityConnection,
) {
    companion object {
        fun create(capacity: Int = Channel.UNLIMITED, kind: ProximityTransportKind = ProximityTransportKind.BLE): FakeProximityLoopback {
            val holderInbound = Channel<ImmutableBytes>(capacity)
            val readerInbound = Channel<ImmutableBytes>(capacity)
            val closure = CompletableDeferred<ProximityCloseReason>()
            return FakeProximityLoopback(
                holder = FakeProximityConnection(holderInbound, readerInbound, kind, closure),
                reader = FakeProximityConnection(readerInbound, holderInbound, kind, closure),
            )
        }
    }
}

class FakeProximityConnection internal constructor(
    private val inbound: Channel<ImmutableBytes>,
    private val outbound: Channel<ImmutableBytes>,
    override val kind: ProximityTransportKind,
    private val closure: CompletableDeferred<ProximityCloseReason>,
) : ProximityConnection {
    private val stateMutex = Mutex()
    private val sendMutex = Mutex()
    private var terminal = false

    override suspend fun awaitClosed(): ProximityCloseReason = closure.await()

    override suspend fun receive(): ImmutableBytes? = inbound.receiveCatching().getOrNull()

    override suspend fun send(message: ImmutableBytes) = sendMutex.withLock {
        stateMutex.withLock { check(!terminal) { "Fake connection is closed" } }
        outbound.send(ImmutableBytes.of(message.copy()))
    }

    override suspend fun close(reason: ProximityCloseReason): Unit = stateMutex.withLock {
        if (terminal) return
        terminal = true
        closure.complete(reason)
        inbound.close()
        outbound.close()
    }

}

class FakePreparedTransport(
    override val connectionMethod: DeviceRetrievalMethod,
    private val connection: ProximityConnection,
) : PreparedTransport {
    override val kind: ProximityTransportKind = connection.kind
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
) : ReaderSelectedTransportProvider {
    override val kind: ProximityTransportKind = connection.kind
    override suspend fun capability(context: EngagementContext): ProximityCapability = availability
    override suspend fun prepare(context: EngagementContext, sessionScope: CoroutineScope): PreparedTransport {
        check(availability.mayPrepare) { "Fake transport is unavailable" }
        return FakePreparedTransport(method, connection)
    }

    override fun acceptsReaderOffer(offer: ReaderSelectedTransportOffer): Boolean =
        offer is ReaderSelectedTransportOffer.Method && offer.value == method

    override suspend fun prepareReaderSelected(
        offer: ReaderSelectedTransportOffer,
        context: EngagementContext,
        sessionScope: CoroutineScope,
    ): PreparedTransport {
        check(acceptsReaderOffer(offer)) { "The fake provider cannot prepare the selected reader offer" }
        return prepare(context, sessionScope)
    }
}
