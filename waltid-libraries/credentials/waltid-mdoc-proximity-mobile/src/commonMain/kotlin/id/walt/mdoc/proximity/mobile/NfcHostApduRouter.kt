package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.proximity.ImmutableBytes
import id.walt.mdoc.proximity.ProximityCloseReason
import id.walt.mdoc.proximity.ProximityConnection
import id.walt.mdoc.proximity.ProximityTransportKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One of the three ISO mdoc applications routed through a single host-card session. */
internal enum class NfcHostApplication { ENGAGEMENT, RETRIEVAL, NFC_V2 }

/**
 * Routes APDUs to the walt-owned common state machines.
 *
 * [process] suspends only when a final ENVELOPE needs the holder protocol engine to produce a
 * response. Android and iOS adapters therefore use one asynchronous response path without owning
 * protocol state.
 */
public class NfcHostApduRouter internal constructor(
    private val engagement: NfcEngagementApduProcessor?,
    private val retrieval: NfcRetrievalApduProcessor?,
    private val nfcV2: NfcV2ApduProcessor?,
    private val canSelectApplication: suspend (NfcHostApplication) -> Boolean = { true },
    private val onApplicationSelected: suspend (NfcHostApplication) -> Unit = {},
    private val onDeactivated: suspend (ProximityCloseReason) -> Unit = {},
) {
    internal val retrievalConnection: NfcApduProximityConnection = NfcApduProximityConnection()
    internal val nfcV2Connection: NfcApduProximityConnection = NfcApduProximityConnection()

    private var selectedApplication: NfcHostApplication? = null
    private val processMutex = Mutex()
    private val deactivationMutex = Mutex()
    private var deactivated = false

    init {
        require(engagement != null || retrieval != null || nfcV2 != null) {
            "An NFC host router requires at least one application"
        }
    }

    /**
     * Processes one encoded command APDU and returns the complete encoded response APDU.
     *
     * Calls are serialized. A command received after deactivation is rejected with
     * `CONDITIONS_NOT_SATISFIED` and cannot revive the session.
     */
    public suspend fun process(encodedCommand: ByteArray): ImmutableBytes = processMutex.withLock {
        if (isDeactivated()) {
            return@withLock status(NfcStatusWord.CONDITIONS_NOT_SATISFIED)
        }
        val decoded = runCatching { NfcCommandApdu.decode(encodedCommand) }.getOrNull()
        val isApplicationSelection = decoded?.isSelectByName() == true
        val selection = decoded?.selectedApplication()
        if (isApplicationSelection && selection == null) return@withLock status(NfcStatusWord.FILE_NOT_FOUND)
        val application = selection ?: selectedApplication
            ?: return@withLock status(NfcStatusWord.CONDITIONS_NOT_SATISFIED)
        if (selection != null && !canSelectApplication(selection)) {
            return@withLock status(NfcStatusWord.CONDITIONS_NOT_SATISFIED)
        }
        if (isDeactivated()) return@withLock status(NfcStatusWord.CONDITIONS_NOT_SATISFIED)
        val response = when (application) {
            NfcHostApplication.ENGAGEMENT -> ImmutableBytes.of(
                requireNotNull(engagement) { "NFC engagement application is unavailable" }.process(encodedCommand)
            )
            NfcHostApplication.RETRIEVAL -> when (
                val result = requireNotNull(retrieval) { "NFC retrieval application is unavailable" }
                    .process(encodedCommand)
            ) {
                is NfcRetrievalApduResult.Response -> result.encoded
                is NfcRetrievalApduResult.Request -> retrievalConnection.exchange(
                    result.identifier,
                    result.sessionMessage,
                    retrieval::cancelPendingResponse,
                ) { identifier, message -> retrieval.completeResponse(identifier, message).copy() }
            }
            NfcHostApplication.NFC_V2 -> when (
                val result = requireNotNull(nfcV2) { "NFCv2 application is unavailable" }.process(encodedCommand)
            ) {
                is NfcV2ApduResult.Response -> result.encoded
                is NfcV2ApduResult.Request -> nfcV2Connection.exchange(
                    result.identifier,
                    result.sessionMessage,
                    nfcV2::cancelPendingResponse,
                ) { identifier, message -> nfcV2.completeResponse(identifier, message).copy() }
            }
        }
        if (selection != null && NfcResponseApdu.decode(response.copy()).statusWord == NfcStatusWord.SUCCESS) {
            if (isDeactivated()) return@withLock status(NfcStatusWord.CONDITIONS_NOT_SATISFIED)
            selectedApplication = selection
            onApplicationSelected(selection)
        }
        response
    }

    /** Invalidates all in-flight callbacks and requires fresh application selection. */
    public suspend fun deactivate(reason: ProximityCloseReason = ProximityCloseReason.PEER_DISCONNECTED) {
        val firstDeactivation = deactivationMutex.withLock {
            if (deactivated) false else true.also { deactivated = true }
        }
        if (!firstDeactivation) return
        var connectionFailure: Throwable? = null
        try {
            try {
                retrievalConnection.close(reason)
            } catch (failure: Throwable) {
                connectionFailure = failure
            }
            try {
                nfcV2Connection.close(reason)
            } catch (failure: Throwable) {
                if (connectionFailure == null) connectionFailure = failure
            }
            processMutex.withLock {
                selectedApplication = null
                engagement?.deactivate()
                retrieval?.deactivate()
                nfcV2?.deactivate()
            }
            connectionFailure?.let { throw it }
        } finally {
            onDeactivated(reason)
        }
    }

    private suspend fun isDeactivated(): Boolean = deactivationMutex.withLock { deactivated }

    private fun NfcCommandApdu.isSelectByName(): Boolean =
        cla == 0.toUByte() && instruction == 0xa4.toUByte() &&
            parameter1 == 0x04.toUByte()

    private fun NfcCommandApdu.selectedApplication(): NfcHostApplication? {
        if (!isSelectByName()) return null
        return when {
            data == MdocNfcAid.NDEF_APPLICATION && engagement != null -> NfcHostApplication.ENGAGEMENT
            data == MdocNfcAid.DATA_TRANSFER && retrieval != null -> NfcHostApplication.RETRIEVAL
            data == MdocNfcAid.NFC_V2 && nfcV2 != null -> NfcHostApplication.NFC_V2
            else -> null
        }
    }

    private fun status(word: UShort): ImmutableBytes =
        ImmutableBytes.of(NfcResponseApdu(statusWord = word).encode())
}

/** Message-level connection backed by one NFC ENVELOPE/response exchange at a time. */
internal class NfcApduProximityConnection : ProximityConnection {
    override val kind: ProximityTransportKind = ProximityTransportKind.NFC

    private class PendingExchange(
        val identifier: ULong,
        val cancel: (ULong) -> Unit,
        val complete: (ULong, ByteArray) -> ByteArray,
        val response: CompletableDeferred<ImmutableBytes>,
    )

    private val incoming = Channel<ImmutableBytes>(capacity = 1)
    private val mutex = Mutex()
    private val pendingExchanges = ArrayDeque<PendingExchange>()
    private val queuedResponses = ArrayDeque<ImmutableBytes>()
    private var closed = false

    internal suspend fun exchange(
        identifier: ULong,
        message: ImmutableBytes,
        cancel: (ULong) -> Unit,
        complete: (ULong, ByteArray) -> ByteArray,
    ): ImmutableBytes {
        val response = CompletableDeferred<ImmutableBytes>()
        val pending = PendingExchange(identifier, cancel, complete, response)
        val queued = mutex.withLock {
            check(!closed) { "NFC connection is closed" }
            queuedResponses.removeFirstOrNull() ?: run {
                pendingExchanges.addLast(pending)
                null
            }
        }
        try {
            incoming.send(ImmutableBytes.of(message.copy()))
            queued?.let { complete(pending, it) }
            return response.await()
        } catch (cancelled: CancellationException) {
            val owned = mutex.withLock {
                val active = response.isActive && pendingExchanges.remove(pending)
                response.cancel(cancelled)
                active
            }
            if (owned) pending.cancel(pending.identifier)
            throw cancelled
        } catch (failure: Throwable) {
            val owned = mutex.withLock {
                response.isActive && pendingExchanges.remove(pending)
            }
            if (owned) pending.cancel(pending.identifier)
            throw failure
        }
    }

    override suspend fun receive(): ImmutableBytes? = incoming.receiveCatching().getOrNull()

    override suspend fun send(message: ImmutableBytes) {
        val pending = mutex.withLock {
            check(!closed) { "NFC connection is closed" }
            requireNotNull(pendingExchanges.removeFirstOrNull()) {
                "No NFC reader request is awaiting a response"
            }
        }
        complete(pending, message)
    }

    /** Queues one response when the same NFCv2 request arrived first on the alternate bearer. */
    internal suspend fun sendOrQueueHybridResponse(message: ImmutableBytes) {
        val pending = mutex.withLock {
            check(!closed) { "NFC connection is closed" }
            pendingExchanges.removeFirstOrNull().also {
                if (it == null) {
                    check(queuedResponses.isEmpty()) {
                        "Only one NFCv2 response may wait for the reader's duplicate NFC request"
                    }
                    queuedResponses.addLast(ImmutableBytes.of(message.copy()))
                }
            }
        }
        pending?.let { complete(it, message) }
    }

    override suspend fun close(reason: ProximityCloseReason) {
        val pending = mutex.withLock {
            if (closed) return
            closed = true
            queuedResponses.clear()
            pendingExchanges.toList().also { pendingExchanges.clear() }
        }
        incoming.close()
        pending.forEach {
            it.cancel(it.identifier)
            it.response.completeExceptionally(
                IllegalStateException("NFC connection closed before the holder response was produced")
            )
        }
    }

    private fun complete(pending: PendingExchange, message: ImmutableBytes) {
        try {
            check(
                pending.response.complete(
                    ImmutableBytes.of(pending.complete(pending.identifier, message.copy()))
                )
            ) { "The NFC reader exchange was completed concurrently" }
        } catch (failure: Throwable) {
            pending.cancel(pending.identifier)
            pending.response.completeExceptionally(failure)
            throw failure
        }
    }
}
