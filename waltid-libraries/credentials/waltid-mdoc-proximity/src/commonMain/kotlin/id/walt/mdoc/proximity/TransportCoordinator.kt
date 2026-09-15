package id.walt.mdoc.proximity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

class PreparedTransports internal constructor(
    transports: List<PreparedTransport>,
    unavailable: Map<ProximityTransportKind, ProximityError>,
) {
    private val ownedTransports = transports.toList()
    private val ownedMethods = ownedTransports.map { it.connectionMethod.snapshot() }
    private val ownedUnavailable = unavailable.toMap()
    val transports: List<PreparedTransport> get() = ownedTransports.toList()
    val unavailable: Map<ProximityTransportKind, ProximityError> get() = ownedUnavailable.toMap()
    init {
        require(transports.isNotEmpty()) { "At least one proximity transport must be prepared" }
        require(transports.map { it.id }.distinct().size == transports.size) {
            "A prepared transport identifier may be used only once"
        }
    }

    val connectionMethods get() = ownedMethods.map { it.snapshot() }

    companion object {
        /** Creates a validated prepared-bearer set for an engagement implementation. */
        fun of(
            transports: List<PreparedTransport>,
            unavailable: Map<ProximityTransportKind, ProximityError> = emptyMap(),
        ): PreparedTransports = PreparedTransports(transports, unavailable)
    }
}

data class WinningConnection(
    val prepared: PreparedTransport,
    val connection: ProximityConnection,
)

class TransportCoordinator {
    suspend fun prepare(
        providers: List<ProximityTransportProvider>,
        context: EngagementContext,
        sessionScope: CoroutineScope,
    ): PreparedTransports = coroutineScope {
        val ownedProviders = providers.toList()
        require(ownedProviders.isNotEmpty()) { "At least one transport provider is required" }
        require(ownedProviders.map { it.id }.distinct().size == ownedProviders.size) {
            "A transport provider identifier may be registered only once"
        }
        val prepared = mutableListOf<PreparedTransport>()
        val unavailable = mutableMapOf<ProximityTransportKind, ProximityError>()
        val preparationFailures = mutableListOf<ProximityException>()
        try {
            ownedProviders.forEach { provider ->
                val capability = try {
                    provider.capability(context)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (cause: Exception) {
                    val failure = cause as? ProximityException ?: ProximityException(
                        ProximityError.Capability("capability_check_failed", "${provider.kind} capability check failed"), cause,
                    )
                    preparationFailures += failure
                    unavailable[provider.kind] = failure.error
                    return@forEach
                }
                if (!capability.mayPrepare) {
                    val error = capability.unavailableReason ?: ProximityError.Capability(
                        "transport_unavailable", "${provider.kind} is not available for the selected profile and runtime",
                    )
                    unavailable[provider.kind] = error
                    preparationFailures += ProximityException(error)
                    return@forEach
                }
                val candidate = try {
                    provider.prepare(context, sessionScope)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (cause: Exception) {
                    val failure = cause as? ProximityException ?: ProximityException(
                        ProximityError.Transport("transport_prepare_failed", "${provider.kind} could not be prepared"), cause,
                    )
                    preparationFailures += failure
                    unavailable[provider.kind] = failure.error
                    return@forEach
                }
                if (candidate.id != provider.id) {
                    withContext(NonCancellable) {
                        runCatching { candidate.close(ProximityCloseReason.CANCELLED) }
                    }
                    throw IllegalArgumentException("A prepared transport must retain its provider identifier")
                }
                prepared += candidate
            }
            if (prepared.isEmpty()) {
                val failure = preparationFailures.firstOrNull { it.error is ProximityError.Capability }
                    ?: ProximityException(
                        ProximityError.Capability("no_transport", "No requested proximity transport could be prepared"),
                        preparationFailures.firstOrNull(),
                    )
                preparationFailures.filter { it !== failure && it !== failure.cause }.forEach(failure::addSuppressed)
                throw failure
            }
            PreparedTransports(prepared.toList(), unavailable.toMap())
        } catch (failure: Throwable) {
            closeAll(prepared, ProximityCloseReason.CANCELLED)
            throw failure
        }
    }

    suspend fun awaitWinner(prepared: PreparedTransports): WinningConnection = supervisorScope {
        val results = Channel<Pair<PreparedTransport, Result<ProximityConnection>>>(prepared.transports.size)
        val jobs = prepared.transports.map { transport ->
            launch {
                var connection: ProximityConnection? = null
                try {
                    val result = try {
                        Result.success(transport.awaitConnection())
                    } catch (cancelled: CancellationException) {
                        currentCoroutineContext().ensureActive()
                        Result.failure(cancelled)
                    } catch (failure: Exception) {
                        Result.failure(failure)
                    }
                    connection = result.getOrNull()
                    results.send(transport to result)
                    connection = null
                } catch (cancelled: CancellationException) {
                    withContext(NonCancellable) {
                        connection?.close(ProximityCloseReason.LOST_RACE)
                    }
                    throw cancelled
                } catch (failure: Throwable) {
                    withContext(NonCancellable) {
                        connection?.close(ProximityCloseReason.LOST_RACE)
                    }
                    throw failure
                }
            }
        }
        var failureCloseReason = ProximityCloseReason.CANCELLED
        try {
            val failures = mutableListOf<Throwable>()
            var winner: WinningConnection? = null
            while (winner == null && failures.size < prepared.transports.size) {
                val (transport, result) = results.receive()
                result.onSuccess { connection -> winner = WinningConnection(transport, connection) }
                    .onFailure { failures += requireNotNull(result.exceptionOrNull()) }
            }
            val selected = winner ?: run {
                failureCloseReason = ProximityCloseReason.PEER_DISCONNECTED
                val failure = failures.filterIsInstance<ProximityException>().firstOrNull()
                    ?: ProximityException(
                        ProximityError.Transport("connection_failed", "All prepared transports failed"), failures.firstOrNull(),
                    )
                failures.filter { it !== failure && it !== failure.cause }.forEach(failure::addSuppressed)
                throw failure
            }
            closeAll(
                prepared.transports.filterNot { it === selected.prepared },
                ProximityCloseReason.LOST_RACE,
            )
            jobs.forEach { if (it.isActive) it.cancelAndJoin() }
            selected
        } catch (failure: Throwable) {
            withContext(NonCancellable) {
                closeAll(prepared.transports, failureCloseReason)
                jobs.forEach { if (it.isActive) it.cancelAndJoin() }
            }
            throw failure
        } finally {
            results.close()
        }
    }

    private suspend fun closeAll(
        transports: Collection<PreparedTransport>,
        reason: ProximityCloseReason,
    ) = withContext(NonCancellable) {
        transports.forEach { transport ->
            try {
                transport.close(reason)
            } catch (_: Exception) {
                // Closing every resource is more important than surfacing an individual close failure.
            }
        }
    }
}
