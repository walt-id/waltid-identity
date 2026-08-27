package id.walt.mdoc.proximity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

@ConsistentCopyVisibility
data class PreparedTransports internal constructor(
    val transports: List<PreparedTransport>,
    val unavailable: Map<ProximityTransportKind, ProximityError>,
) {
    init {
        require(transports.isNotEmpty()) { "At least one proximity transport must be prepared" }
        require(transports.map { it.kind }.distinct().size == transports.size) {
            "A transport kind may be prepared only once"
        }
    }

    val connectionMethods get() = transports.map { it.connectionMethod }
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
        require(providers.isNotEmpty()) { "At least one transport provider is required" }
        require(providers.map { it.kind }.distinct().size == providers.size) {
            "A transport provider kind may be registered only once"
        }
        val prepared = mutableListOf<PreparedTransport>()
        val unavailable = mutableMapOf<ProximityTransportKind, ProximityError>()
        try {
            providers.forEach { provider ->
                val capability = try {
                    provider.capability(context)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (cause: Exception) {
                    unavailable[provider.kind] = ProximityError.Capability(
                        "capability_check_failed",
                        "${provider.kind} capability check failed",
                    )
                    return@forEach
                }
                if (!capability.mayPrepare) {
                    unavailable[provider.kind] = capability.unavailableReason ?: ProximityError.Capability(
                        "transport_unavailable",
                        "${provider.kind} is not available for the selected profile and runtime",
                    )
                    return@forEach
                }
                try {
                    prepared += provider.prepare(context, sessionScope)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (cause: Exception) {
                    unavailable[provider.kind] = ProximityError.Transport(
                        "transport_prepare_failed",
                        "${provider.kind} could not be prepared",
                    )
                }
            }
            if (prepared.isEmpty()) throw ProximityException(
                ProximityError.Capability("no_transport", "No requested proximity transport could be prepared")
            )
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
                        throw cancelled
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
            var failures = 0
            var winner: WinningConnection? = null
            while (winner == null && failures < prepared.transports.size) {
                val (transport, result) = results.receive()
                result.onSuccess { connection -> winner = WinningConnection(transport, connection) }
                    .onFailure { failures++ }
            }
            jobs.forEach { if (it.isActive) it.cancelAndJoin() }
            val selected = winner ?: run {
                failureCloseReason = ProximityCloseReason.PEER_DISCONNECTED
                throw ProximityException(ProximityError.Transport("connection_failed", "All prepared transports failed"))
            }
            closeAll(
                prepared.transports.filterNot { it === selected.prepared },
                ProximityCloseReason.LOST_RACE,
            )
            selected
        } catch (failure: Throwable) {
            withContext(NonCancellable) {
                jobs.forEach { if (it.isActive) it.cancelAndJoin() }
                closeAll(prepared.transports, failureCloseReason)
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
