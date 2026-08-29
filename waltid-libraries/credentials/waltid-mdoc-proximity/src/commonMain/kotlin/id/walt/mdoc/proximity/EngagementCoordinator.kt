package id.walt.mdoc.proximity

import id.walt.crypto2.keys.Key
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Inputs shared by every engagement source prepared for one holder session. */
data class MdocEngagementPreparationContext(
    val eDeviceKey: Key,
    val engagementContext: EngagementContext,
    val capabilities: MdocSessionCapabilities,
    val limits: MdocProximityLimits,
)

/** Display-safe facts available before a reader has selected an engagement path. */
data class MdocEngagementReadiness(
    val qrPayload: String?,
    val availableTransports: Set<ProximityTransportKind>,
    val unavailableTransports: Map<ProximityTransportKind, ProximityError>,
) {
    init {
        require(qrPayload == null || qrPayload.isNotBlank()) { "A QR payload must not be blank" }
        require(availableTransports.intersect(unavailableTransports.keys).isEmpty()) {
            "A transport cannot be both available and unavailable"
        }
    }
}

/** Exact protocol material and live bearer selected by one completed engagement. */
data class MdocEngagedConnection(
    val engagementMode: MdocEngagementMode,
    val deviceEngagement: ImmutableBytes,
    val sessionHandover: MdocSessionHandover,
    val connection: ProximityConnection,
) {
    init {
        require(deviceEngagement.size > 0) { "DeviceEngagement must not be empty" }
        require(
            engagementMode is MdocEngagementMode.Qr && sessionHandover == MdocSessionHandover.Qr ||
                engagementMode is MdocEngagementMode.Nfc && sessionHandover != MdocSessionHandover.Qr
        ) {
            "The session handover must match the selected engagement mode"
        }
    }
}

/** One session-scoped QR or NFC engagement source, including every retrieval resource it advertises. */
interface PreparedMdocEngagement {
    val modes: Set<MdocEngagementMode>
    val readiness: MdocEngagementReadiness

    /** Waits until this engagement path produces an exact handover and a connected bearer. */
    suspend fun awaitConnection(): MdocEngagedConnection

    /** Idempotently closes this source, its unselected bearers, and any selected bearer it owns. */
    suspend fun close(reason: ProximityCloseReason)
}

/** Prepares one independent engagement path. Platform adapters remain behind implementations. */
interface MdocEngagementSource {
    val modes: Set<MdocEngagementMode>

    suspend fun prepare(
        context: MdocEngagementPreparationContext,
        sessionScope: CoroutineScope,
    ): PreparedMdocEngagement
}

@ConsistentCopyVisibility
data class PreparedMdocEngagements internal constructor(
    val sources: List<PreparedMdocEngagement>,
    val readiness: MdocEngagementReadiness,
) {
    init {
        require(sources.isNotEmpty()) { "At least one engagement source must be prepared" }
        require(sources.flatMap { it.modes }.distinct().size == sources.sumOf { it.modes.size }) {
            "An engagement mode may be prepared by only one source"
        }
    }
}

data class WinningMdocEngagement(
    val source: PreparedMdocEngagement,
    val engaged: MdocEngagedConnection,
)

/** Owns the QR-versus-NFC race and deterministic loser cleanup. */
class MdocEngagementCoordinator {
    suspend fun prepare(
        sources: List<MdocEngagementSource>,
        context: MdocEngagementPreparationContext,
        sessionScope: CoroutineScope,
    ): PreparedMdocEngagements = coroutineScope {
        require(sources.isNotEmpty()) { "At least one engagement source is required" }
        require(sources.all { it.modes.isNotEmpty() }) { "An engagement source must own at least one mode" }
        require(sources.flatMap { it.modes }.distinct().size == sources.sumOf { it.modes.size }) {
            "An engagement mode may be registered by only one source"
        }
        val prepared = mutableListOf<PreparedMdocEngagement>()
        try {
            sources.forEach { source ->
                val candidate = try {
                    source.prepare(context, sessionScope)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: ProximityException) {
                    if (failure.error !is ProximityError.Capability) throw failure
                    // Another configured engagement path may still be usable.
                    return@forEach
                }
                if (candidate.modes != source.modes) {
                    withContext(NonCancellable) {
                        runCatching { candidate.close(ProximityCloseReason.CANCELLED) }
                    }
                    throw IllegalArgumentException("A prepared engagement must retain its source modes")
                }
                require((MdocEngagementMode.Qr in candidate.modes) == (candidate.readiness.qrPayload != null)) {
                    "Only a prepared QR engagement may expose a QR payload"
                }
                prepared += candidate
            }
            if (prepared.isEmpty()) throw ProximityException(
                ProximityError.Capability("no_engagement", "No requested engagement method could be prepared")
            )
            val qrPayloads = prepared.mapNotNull { it.readiness.qrPayload }
            require(qrPayloads.size <= 1) { "Only one prepared engagement may expose a QR payload" }
            val availableTransports = prepared.flatMap { it.readiness.availableTransports }.toSet()
            PreparedMdocEngagements(
                sources = prepared.toList(),
                readiness = MdocEngagementReadiness(
                    qrPayload = qrPayloads.singleOrNull(),
                    availableTransports = availableTransports,
                    unavailableTransports = prepared
                        .flatMap { it.readiness.unavailableTransports.entries }
                        .associate { it.toPair() }
                        .filterKeys { it !in availableTransports },
                ),
            )
        } catch (failure: Throwable) {
            closeAll(prepared, ProximityCloseReason.CANCELLED)
            throw failure
        }
    }

    suspend fun awaitWinner(prepared: PreparedMdocEngagements): WinningMdocEngagement = supervisorScope {
        val results = Channel<Pair<PreparedMdocEngagement, Result<MdocEngagedConnection>>>(prepared.sources.size)
        val jobs = prepared.sources.map { source ->
            launch {
                try {
                    val result = try {
                        Result.success(source.awaitConnection())
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        Result.failure(failure)
                    }
                    results.send(source to result)
                } catch (cancelled: CancellationException) {
                    withContext(NonCancellable) { source.close(ProximityCloseReason.LOST_RACE) }
                    throw cancelled
                }
            }
        }
        var failureCloseReason = ProximityCloseReason.CANCELLED
        try {
            var failures = 0
            var winner: WinningMdocEngagement? = null
            while (winner == null && failures < prepared.sources.size) {
                val (source, result) = results.receive()
                val engaged = result.getOrNull()
                if (engaged == null) {
                    failures++
                } else {
                    require(engaged.engagementMode in source.modes) {
                        "An engaged connection must use a mode owned by its source"
                    }
                    winner = WinningMdocEngagement(source, engaged)
                }
            }
            jobs.forEach { if (it.isActive) it.cancelAndJoin() }
            val selected = winner ?: run {
                failureCloseReason = ProximityCloseReason.PEER_DISCONNECTED
                throw ProximityException(ProximityError.Transport("engagement_failed", "All engagement paths failed"))
            }
            closeAll(prepared.sources.filterNot { it === selected.source }, ProximityCloseReason.LOST_RACE)
            selected
        } catch (failure: Throwable) {
            withContext(NonCancellable) {
                jobs.forEach { if (it.isActive) it.cancelAndJoin() }
                closeAll(prepared.sources, failureCloseReason)
            }
            throw failure
        } finally {
            results.close()
        }
    }

    private suspend fun closeAll(
        sources: Collection<PreparedMdocEngagement>,
        reason: ProximityCloseReason,
    ) = withContext(NonCancellable) {
        sources.forEach { source -> runCatching { source.close(reason) } }
    }
}

/** Existing QR engagement expressed through the same source boundary used by NFC. */
class QrMdocEngagementSource(
    private val transportProviders: List<ProximityTransportProvider>,
    private val transportCoordinator: TransportCoordinator = TransportCoordinator(),
    private val engagementFactory: MdocDeviceEngagementFactory = MdocDeviceEngagementFactory(),
) : MdocEngagementSource {
    override val modes: Set<MdocEngagementMode> = setOf(MdocEngagementMode.Qr)

    override suspend fun prepare(
        context: MdocEngagementPreparationContext,
        sessionScope: CoroutineScope,
    ): PreparedMdocEngagement {
        val engagementContext = context.engagementContext.copy(engagementMode = MdocEngagementMode.Qr)
        val transports = transportCoordinator.prepare(transportProviders, engagementContext, sessionScope)
        return try {
            val engagement = engagementFactory.create(
                context.eDeviceKey,
                transports.connectionMethods,
                engagementContext,
                context.capabilities,
            )
            val exact = ImmutableBytes.of(engagement.engagement.encodedCopy())
            context.limits.requireEngagementOrHandover(exact)
            PreparedQrMdocEngagement(
                transports = transports,
                engagement = exact,
                qrPayload = requireNotNull(engagement.qrPayload),
                transportCoordinator = transportCoordinator,
            )
        } catch (failure: Throwable) {
            withContext(NonCancellable) {
                transports.transports.forEach { runCatching { it.close(ProximityCloseReason.CANCELLED) } }
            }
            throw failure
        }
    }
}

private class PreparedQrMdocEngagement(
    private val transports: PreparedTransports,
    private val engagement: ImmutableBytes,
    qrPayload: String,
    private val transportCoordinator: TransportCoordinator,
) : PreparedMdocEngagement {
    override val modes: Set<MdocEngagementMode> = setOf(MdocEngagementMode.Qr)
    override val readiness: MdocEngagementReadiness = MdocEngagementReadiness(
        qrPayload = qrPayload,
        availableTransports = transports.transports.map { it.kind }.toSet(),
        unavailableTransports = transports.unavailable,
    )
    private val closeMutex = Mutex()
    private var closed = false

    override suspend fun awaitConnection(): MdocEngagedConnection {
        val winner = transportCoordinator.awaitWinner(transports)
        return MdocEngagedConnection(
            engagementMode = MdocEngagementMode.Qr,
            deviceEngagement = engagement,
            sessionHandover = MdocSessionHandover.Qr,
            connection = winner.connection,
        )
    }

    override suspend fun close(reason: ProximityCloseReason) {
        val shouldClose = closeMutex.withLock {
            if (closed) false else true.also { closed = true }
        }
        if (!shouldClose) return
        withContext(NonCancellable) {
            transports.transports.forEach { runCatching { it.close(reason) } }
        }
    }
}
