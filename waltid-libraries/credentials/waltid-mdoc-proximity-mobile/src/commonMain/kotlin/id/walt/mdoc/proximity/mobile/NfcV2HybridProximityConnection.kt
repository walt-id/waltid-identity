package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.proximity.ImmutableBytes
import id.walt.mdoc.proximity.PreparedTransport
import id.walt.mdoc.proximity.ProximityCloseReason
import id.walt.mdoc.proximity.ProximityConnection
import id.walt.mdoc.proximity.ProximityError
import id.walt.mdoc.proximity.ProximityException
import id.walt.mdoc.proximity.ProximityTransportKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Provisional NFCv2 hybrid message path.
 *
 * NFC remains live while the reader-selected bearer connects. Exact outgoing messages are copied to
 * both bearers, and the first exact incoming message at each bearer-local ordinal is conveyed once.
 * A later duplicate must be byte-identical; divergent duplicates fail closed before session crypto
 * observes the same sequence position twice.
 */
internal class NfcV2HybridProximityConnection(
    private val nfc: NfcApduProximityConnection,
    private val alternate: PreparedTransport,
    sessionScope: CoroutineScope,
    private val maximumMessagesPerDirection: Long,
) : ProximityConnection {
    override val kind: ProximityTransportKind = alternate.kind

    private enum class Bearer { NFC, ALTERNATE }

    private sealed interface IncomingEvent {
        data class Message(
            val ordinal: Long,
            val bytes: ImmutableBytes,
        ) : IncomingEvent

        data object BearerEnded : IncomingEvent
    }

    private data class AlternateSend(
        val bytes: ImmutableBytes,
        val completion: CompletableDeferred<Result<Unit>>,
    )

    private val lifecycleMutex = Mutex()
    private val receiveMutex = Mutex()
    private val sendMutex = Mutex()
    private val incomingEvents = Channel<IncomingEvent>(Channel.BUFFERED)
    private val alternateSends = Channel<AlternateSend>(Channel.BUFFERED)
    private val retainedIncoming = mutableListOf<ImmutableBytes>()
    private val childJob = SupervisorJob(sessionScope.coroutineContext[Job])
    private val scope = CoroutineScope(sessionScope.coroutineContext + childJob)

    private var alternateConnection: ProximityConnection? = null
    private var nfcEnded = false
    private var alternateEnded = false
    private var nfcFailure: Throwable? = null
    private var alternateFailure: Throwable? = null
    private var sentMessages = 0L
    private var closed = false

    init {
        require(maximumMessagesPerDirection > 0) {
            "An NFCv2 hybrid connection requires a positive message limit"
        }
        scope.launch { receiveFrom(Bearer.NFC, nfc) }
        scope.launch { connectAlternate() }
    }

    override suspend fun receive(): ImmutableBytes? {
        if (!receiveMutex.tryLock()) throw ProximityException(
            ProximityError.Transport(
                "concurrent_receive",
                "An NFCv2 hybrid connection supports one receive consumer",
            )
        )
        try {
            while (true) {
                val event = incomingEvents.receiveCatching().getOrNull() ?: return null
                when (event) {
                    is IncomingEvent.Message -> when {
                        event.ordinal < retainedIncoming.size.toLong() -> {
                            val original = retainedIncoming[event.ordinal.toInt()]
                            if (original != event.bytes) throw hybridProtocolFailure(
                                "nfc_v2_hybrid_duplicate_mismatch",
                                "NFCv2 bearers delivered different bytes for the same message ordinal",
                            )
                        }
                        event.ordinal > retainedIncoming.size.toLong() -> throw hybridProtocolFailure(
                            "nfc_v2_hybrid_message_gap",
                            "An NFCv2 bearer skipped a message ordinal",
                        )
                        else -> {
                            if (retainedIncoming.size.toLong() >= maximumMessagesPerDirection) {
                                throw hybridProtocolFailure(
                                    "nfc_v2_hybrid_message_limit",
                                    "The NFCv2 hybrid connection exceeded its message limit",
                                )
                            }
                            retainedIncoming += event.bytes
                            return event.bytes
                        }
                    }
                    IncomingEvent.BearerEnded -> {
                        val terminal = lifecycleMutex.withLock { nfcEnded && alternateEnded }
                        if (terminal) {
                            val failure = lifecycleMutex.withLock { alternateFailure ?: nfcFailure }
                            if (failure != null) throw hybridTransportFailure(
                                "nfc_v2_hybrid_receive_failed",
                                "Both NFCv2 hybrid bearers ended before another message was received",
                                failure,
                            )
                            return null
                        }
                    }
                }
            }
        } finally {
            receiveMutex.unlock()
        }
    }

    override suspend fun send(message: ImmutableBytes) = sendMutex.withLock {
        val snapshot = ImmutableBytes.of(message.copy())
        lifecycleMutex.withLock {
            if (closed) throw hybridTransportFailure(
                "nfc_v2_hybrid_closed",
                "The NFCv2 hybrid connection is closed",
                null,
            )
            if (sentMessages >= maximumMessagesPerDirection) throw hybridProtocolFailure(
                "nfc_v2_hybrid_message_limit",
                "The NFCv2 hybrid connection exceeded its outgoing message limit",
            )
            sentMessages++
        }

        val nfcResult = try {
            nfc.sendOrQueueHybridResponse(snapshot)
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            currentCoroutineContext().ensureActive()
            endBearer(Bearer.NFC, cancelled)
            Result.failure(cancelled)
        } catch (failure: Throwable) {
            endBearer(Bearer.NFC, failure)
            Result.failure(failure)
        }

        val alternateCompletion = CompletableDeferred<Result<Unit>>()
        val alternateQueued = try {
            alternateSends.send(AlternateSend(snapshot, alternateCompletion))
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            currentCoroutineContext().ensureActive()
            Result.failure(cancelled)
        } catch (failure: Throwable) {
            Result.failure(failure)
        }
        if (alternateQueued.isFailure) {
            if (nfcResult.isSuccess) return@withLock
            throw hybridTransportFailure(
                "nfc_v2_hybrid_send_failed",
                "Neither NFCv2 hybrid bearer accepted the outgoing message",
                nfcResult.exceptionOrNull() ?: alternateQueued.exceptionOrNull(),
            )
        }

        if (nfcResult.isSuccess) return@withLock

        val alternateResult = alternateCompletion.await()
        if (alternateResult.isFailure) throw hybridTransportFailure(
            "nfc_v2_hybrid_send_failed",
            "Both NFCv2 hybrid bearers failed to send the outgoing message",
            alternateResult.exceptionOrNull() ?: nfcResult.exceptionOrNull(),
        )
    }

    override suspend fun close(reason: ProximityCloseReason) = withContext(NonCancellable) {
        val shouldClose = lifecycleMutex.withLock {
            if (closed) {
                false
            } else {
                closed = true
                true
            }
        }
        if (!shouldClose) return@withContext
        val closeFailure = CancellationException("NFCv2 hybrid connection closed: $reason")
        alternateSends.close()
        failQueuedAlternateSends(closeFailure)

        var firstFailure: Throwable? = null
        suspend fun attempt(block: suspend () -> Unit) {
            try {
                block()
            } catch (failure: Throwable) {
                if (firstFailure == null) firstFailure = failure
            }
        }

        // Release platform I/O before joining workers: receive/send implementations may only
        // unblock when their owning prepared transport is closed.
        attempt { nfc.close(reason) }
        attempt { alternate.close(reason) }
        childJob.cancel()
        attempt { childJob.join() }
        incomingEvents.close()
        firstFailure?.let { throw it }
        Unit
    }

    private suspend fun connectAlternate() {
        val connection = try {
            alternate.awaitConnection()
        } catch (cancelled: CancellationException) {
            currentCoroutineContext().ensureActive()
            endBearer(Bearer.ALTERNATE, cancelled)
            return
        } catch (failure: Throwable) {
            endBearer(Bearer.ALTERNATE, failure)
            return
        }
        val accepted = lifecycleMutex.withLock {
            if (closed || alternateEnded) false else true.also { alternateConnection = connection }
        }
        if (!accepted) {
            connection.close(ProximityCloseReason.CANCELLED)
            return
        }
        scope.launch { sendToAlternate(connection) }
        scope.launch { receiveFrom(Bearer.ALTERNATE, connection) }
    }

    private suspend fun sendToAlternate(connection: ProximityConnection) {
        try {
            for (outgoing in alternateSends) {
                try {
                    connection.send(outgoing.bytes)
                    outgoing.completion.complete(Result.success(Unit))
                } catch (cancelled: CancellationException) {
                    try {
                        currentCoroutineContext().ensureActive()
                    } catch (scopeCancelled: CancellationException) {
                        outgoing.completion.cancel(scopeCancelled)
                        throw scopeCancelled
                    }
                    outgoing.completion.complete(Result.failure(cancelled))
                    endBearer(Bearer.ALTERNATE, cancelled)
                    return
                } catch (failure: Throwable) {
                    outgoing.completion.complete(Result.failure(failure))
                    endBearer(Bearer.ALTERNATE, failure)
                    return
                }
            }
        } finally {
            failQueuedAlternateSends(
                alternateFailure ?: CancellationException("NFCv2 alternate bearer stopped"),
            )
        }
    }

    private suspend fun receiveFrom(bearer: Bearer, connection: ProximityConnection) {
        var ordinal = 0L
        try {
            while (true) {
                val message = connection.receive() ?: break
                incomingEvents.send(IncomingEvent.Message(ordinal, ImmutableBytes.of(message.copy())))
                ordinal++
            }
            endBearer(bearer, null)
        } catch (cancelled: CancellationException) {
            currentCoroutineContext().ensureActive()
            endBearer(bearer, cancelled)
        } catch (failure: Throwable) {
            endBearer(bearer, failure)
        }
    }

    private suspend fun endBearer(bearer: Bearer, failure: Throwable?) {
        var alternateToClose: ProximityConnection? = null
        var terminal = false
        val first = lifecycleMutex.withLock {
            if (closed) false else when (bearer) {
                Bearer.NFC -> if (nfcEnded) false else true.also {
                    nfcEnded = true
                    nfcFailure = failure
                    terminal = alternateEnded
                }
                Bearer.ALTERNATE -> if (alternateEnded) false else true.also {
                    alternateEnded = true
                    alternateFailure = failure
                    alternateToClose = alternateConnection
                    terminal = nfcEnded
                }
            }
        }
        if (!first) return
        if (bearer == Bearer.ALTERNATE) {
            val sendFailure = failure ?: IllegalStateException("NFCv2 alternate bearer ended")
            alternateSends.close()
            failQueuedAlternateSends(sendFailure)
            try {
                alternateToClose?.close(
                    when {
                        failure == null -> ProximityCloseReason.PEER_DISCONNECTED
                        failure is ProximityException && failure.error is ProximityError.Protocol ->
                            ProximityCloseReason.PROTOCOL_ERROR
                        else -> ProximityCloseReason.PEER_DISCONNECTED
                    },
                )
            } catch (_: Throwable) {
                // The recorded bearer failure remains authoritative; session close retries cleanup.
            }
        }
        try {
            incomingEvents.send(IncomingEvent.BearerEnded)
            if (terminal) incomingEvents.close()
        } catch (cancelled: CancellationException) {
            currentCoroutineContext().ensureActive()
            if (!lifecycleMutex.withLock { closed }) throw cancelled
        }
    }

    private fun failQueuedAlternateSends(failure: Throwable) {
        while (true) {
            val outgoing = alternateSends.tryReceive().getOrNull() ?: return
            outgoing.completion.complete(Result.failure(failure))
        }
    }

    private fun hybridProtocolFailure(code: String, message: String): ProximityException =
        ProximityException(ProximityError.Protocol(code, message))

    private fun hybridTransportFailure(code: String, message: String, cause: Throwable?): ProximityException =
        ProximityException(ProximityError.Transport(code, message), cause)
}
