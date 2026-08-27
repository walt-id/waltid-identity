@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.objects.engagement.BleCentralMode
import id.walt.mdoc.objects.engagement.BlePeripheralMode
import id.walt.mdoc.objects.engagement.DeviceRetrievalMethod
import id.walt.mdoc.proximity.EngagementContext
import id.walt.mdoc.proximity.ImmutableBytes
import id.walt.mdoc.proximity.PreparedTransport
import id.walt.mdoc.proximity.ProximityCapability
import id.walt.mdoc.proximity.ProximityCloseReason
import id.walt.mdoc.proximity.ProximityConnection
import id.walt.mdoc.proximity.ProximityError
import id.walt.mdoc.proximity.ProximityException
import id.walt.mdoc.proximity.ProximityTransportKind
import id.walt.mdoc.proximity.ProximityTransportProvider
import id.walt.mdoc.proximity.SessionTranscriptFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

internal class DefaultBleProximityTransportProvider(
    private val configuration: BleProximityTransportConfiguration,
    private val platform: BlePlatformAdapter,
) : ProximityTransportProvider {
    override val kind: ProximityTransportKind = ProximityTransportKind.BLE

    override suspend fun capability(context: EngagementContext): ProximityCapability {
        val platformCapability = platform.capability()
        val unavailable = if (platformCapability.available) null else ProximityError.Capability(
            platformCapability.code,
            platformCapability.message,
        )
        return ProximityCapability(
            implemented = true,
            profilePermitted = true,
            runtimeAvailable = platformCapability.available,
            sessionSelected = platformCapability.available,
            unavailableReason = unavailable,
        )
    }

    override suspend fun prepare(context: EngagementContext, sessionScope: CoroutineScope): PreparedTransport {
        val capability = capability(context)
        if (!capability.mayPrepare) throw ProximityException(
            capability.unavailableReason ?: ProximityError.Capability("ble_unavailable", "BLE is unavailable")
        )
        val expectedIdent = BleIdent.derive(configuration.eDeviceKeyBytes)
        val preferL2cap = configuration.bearerPolicy is BleBearerPolicy.PreferL2cap
        val roles = mutableListOf<BlePreparedPlatformRole>()
        val failures = mutableListOf<Throwable>()
        try {
            suspend fun prepareCentral(uuid: BleServiceUuid) {
                try {
                    roles += withTimeout(BLE_SETUP_TIMEOUT) {
                        platform.prepareCentralClient(uuid, expectedIdent, preferL2cap, sessionScope)
                    }
                } catch (_: TimeoutCancellationException) {
                    failures += ProximityException(
                        ProximityError.Transport("ble_prepare_timeout", "BLE central-client preparation timed out")
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    failures += failure
                }
            }
            suspend fun preparePeripheral(uuid: BleServiceUuid) {
                try {
                    roles += withTimeout(BLE_SETUP_TIMEOUT) {
                        platform.preparePeripheralServer(uuid, preferL2cap, sessionScope)
                    }
                } catch (_: TimeoutCancellationException) {
                    failures += ProximityException(
                        ProximityError.Transport("ble_prepare_timeout", "BLE peripheral-server preparation timed out")
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    failures += failure
                }
            }
            when (val configuredRoles = configuration.roles) {
                is BleMdocRoles.CentralClient -> prepareCentral(configuredRoles.readerServiceUuid)
                is BleMdocRoles.PeripheralServer -> preparePeripheral(configuredRoles.mdocServiceUuid)
                is BleMdocRoles.Dual -> {
                    prepareCentral(configuredRoles.readerServiceUuid)
                    preparePeripheral(configuredRoles.mdocServiceUuid)
                }
            }
            if (roles.isEmpty()) throw ProximityException(
                ProximityError.Transport("ble_prepare_failed", "No configured BLE role could be prepared"),
                failures.firstOrNull(),
            )
            return PreparedBleTransport(
                roles = roles.toList(),
                maximumMessageBytes = context.maximumMessageBytes,
                sessionTranscriptFactory = configuration.sessionTranscriptFactory,
                sessionJob = sessionScope.coroutineContext[Job],
            )
        } catch (failure: Throwable) {
            roles.forEach { it.close(ProximityCloseReason.CANCELLED) }
            throw failure
        } finally {
            expectedIdent.fill(0)
        }
    }

    private companion object {
        val BLE_SETUP_TIMEOUT = 30.seconds
    }
}

private class PreparedBleTransport(
    private val roles: List<BlePreparedPlatformRole>,
    private val maximumMessageBytes: Int,
    override val sessionTranscriptFactory: SessionTranscriptFactory,
    sessionJob: Job?,
) : PreparedTransport {
    override val kind: ProximityTransportKind = ProximityTransportKind.BLE
    override val connectionMethod: DeviceRetrievalMethod = DeviceRetrievalMethod.Ble(
        peripheralMode = roles.singleOrNull { it.role == BlePlatformRole.PERIPHERAL_SERVER }?.let {
            BlePeripheralMode(it.serviceUuid.encoded().copy(), psm = it.l2capPsm)
        },
        centralMode = roles.singleOrNull { it.role == BlePlatformRole.CENTRAL_CLIENT }?.let {
            BleCentralMode(it.serviceUuid.encoded().copy())
        },
    )

    private val closeMutex = Mutex()
    private var awaitStarted = false
    private var closed = false
    private val sessionCompletion = sessionJob?.invokeOnCompletion {
        roles.forEach { role -> role.close(ProximityCloseReason.CANCELLED) }
    }

    override suspend fun awaitConnection(): ProximityConnection = supervisorScope {
        closeMutex.withLock {
            if (closed || awaitStarted) throw ProximityException(
                ProximityError.Transport("ble_listener_unavailable", "A prepared BLE listener can be awaited only once")
            )
            awaitStarted = true
        }
        val results = Channel<Pair<BlePreparedPlatformRole, Result<BleRawConnection>>>(roles.size)
        val jobs = roles.map { role ->
            launch {
                var delivered: BleRawConnection? = null
                try {
                    val result = try {
                        Result.success(role.awaitConnection())
                    } catch (cancelled: CancellationException) {
                        currentCoroutineContext().ensureActive()
                        Result.failure(cancelled)
                    } catch (failure: Exception) {
                        Result.failure(failure)
                    }
                    delivered = result.getOrNull()
                    results.send(role to result)
                    delivered = null
                } finally {
                    delivered?.close(ProximityCloseReason.LOST_RACE)
                }
            }
        }
        try {
            var failures = 0
            val causes = mutableListOf<Throwable>()
            var winner: Pair<BlePreparedPlatformRole, BleRawConnection>? = null
            try {
                withTimeout(BLE_CONNECTION_TIMEOUT) {
                    while (winner == null && failures < roles.size) {
                        val (role, result) = results.receive()
                        result.onSuccess { winner = role to it }.onFailure {
                            failures++
                            causes += it
                        }
                    }
                }
            } catch (_: TimeoutCancellationException) {
                throw ProximityException(
                    ProximityError.Transport("ble_connection_timeout", "No BLE peer connected within 30 seconds")
                )
            }
            jobs.forEach { if (it.isActive) it.cancelAndJoin() }
            val selected = winner ?: throw ProximityException(
                ProximityError.Transport("ble_connection_failed", "Every prepared BLE role failed"),
                causes.firstOrNull(),
            )
            roles.filterNot { it === selected.first }.forEach { it.close(ProximityCloseReason.LOST_RACE) }
            BleMessageConnection(selected.second, maximumMessageBytes)
        } catch (failure: Throwable) {
            withContext(NonCancellable) {
                jobs.forEach { if (it.isActive) it.cancelAndJoin() }
                val reason = if (
                    failure is ProximityException && failure.error.code == "ble_connection_timeout"
                ) ProximityCloseReason.TIMEOUT else ProximityCloseReason.CANCELLED
                roles.forEach { it.close(reason) }
            }
            throw failure
        } finally {
            results.close()
        }
    }

    override suspend fun close(reason: ProximityCloseReason) {
        withContext(NonCancellable) {
            closeMutex.withLock {
                if (!closed) {
                    closed = true
                    sessionCompletion?.dispose()
                    roles.forEach { it.close(reason) }
                }
            }
        }
    }

    private companion object {
        val BLE_CONNECTION_TIMEOUT = 30.seconds
    }
}

private class BleMessageConnection(
    private val raw: BleRawConnection,
    maximumMessageBytes: Int,
) : ProximityConnection {
    override val kind: ProximityTransportKind = ProximityTransportKind.BLE
    private val sendMutex = Mutex()
    private val receiveMutex = Mutex()
    private val closeMutex = Mutex()
    private val gattCodec = BleGattMessageCodec(maximumMessageBytes)
    private val l2capDecoder = BleL2capMessageDecoder(maximumMessageBytes)
    private val decodedL2cap = ArrayDeque<ImmutableBytes>()
    private val maximumMessageBytes = maximumMessageBytes
    private var closed = false

    override suspend fun receive(): ImmutableBytes? {
        if (!receiveMutex.tryLock()) throw ProximityException(
            ProximityError.Transport("concurrent_receive", "A BLE connection supports one receive consumer")
        )
        try {
            decodedL2cap.removeFirstOrNull()?.let { return it }
            while (true) {
                val result = try {
                    withTimeout(BLE_INACTIVITY_TIMEOUT) { raw.incoming.receiveCatching() }
                } catch (_: TimeoutCancellationException) {
                    terminate(ProximityCloseReason.TIMEOUT)
                    throw ProximityException(
                        ProximityError.Transport("ble_inactivity_timeout", "The BLE peer was inactive for 30 seconds")
                    )
                }
                if (result.isClosed) {
                    result.exceptionOrNull()?.let { failure ->
                        if (failure is CancellationException) {
                            terminate(ProximityCloseReason.CANCELLED)
                            throw failure
                        }
                        val reason = if (
                            failure is ProximityException && failure.error is ProximityError.Protocol
                        ) ProximityCloseReason.PROTOCOL_ERROR else ProximityCloseReason.PEER_DISCONNECTED
                        terminate(reason)
                        throw normalizedTransportFailure(
                            failure,
                            code = "ble_receive_failed",
                            message = "The BLE bearer failed while receiving data",
                        )
                    }
                    val truncated = when (raw.bearer) {
                        BleRawBearer.GATT -> gattCodec.hasIncompleteMessage()
                        BleRawBearer.L2CAP -> l2capDecoder.hasIncompleteFrame()
                    }
                    if (truncated) {
                        terminate(ProximityCloseReason.PROTOCOL_ERROR)
                        throw ProximityException(
                            ProximityError.Protocol("truncated_ble_message", "The BLE bearer ended mid-message")
                        )
                    }
                    terminate(ProximityCloseReason.PEER_DISCONNECTED)
                    return null
                }
                val bytes = result.getOrThrow()
                try {
                    when (raw.bearer) {
                        BleRawBearer.GATT -> gattCodec.decode(bytes)?.let { return it }
                        BleRawBearer.L2CAP -> {
                            decodedL2cap.addAll(l2capDecoder.feed(bytes))
                            decodedL2cap.removeFirstOrNull()?.let { return it }
                        }
                    }
                } catch (failure: ProximityException) {
                    if (failure.error is ProximityError.Protocol) terminate(ProximityCloseReason.PROTOCOL_ERROR)
                    throw failure
                }
            }
        } finally {
            receiveMutex.unlock()
        }
    }

    override suspend fun send(message: ImmutableBytes) = sendMutex.withLock {
        closeMutex.withLock {
            if (closed) throw ProximityException(ProximityError.Transport("ble_closed", "The BLE connection is closed"))
        }
        try {
            when (raw.bearer) {
                BleRawBearer.GATT -> {
                    val maximumPacketBytes = raw.maximumGattPacketBytes ?: throw ProximityException(
                        ProximityError.Transport("missing_gatt_mtu", "The BLE GATT connection has no negotiated packet size")
                    )
                    gattCodec.encode(message, maximumPacketBytes).forEach { packet ->
                        withTimeout(BLE_INACTIVITY_TIMEOUT) { raw.write(packet) }
                    }
                }
                BleRawBearer.L2CAP -> withTimeout(BLE_INACTIVITY_TIMEOUT) {
                    raw.write(BleL2capMessageCodec.encode(message, maximumMessageBytes))
                }
            }
        } catch (_: TimeoutCancellationException) {
            terminate(ProximityCloseReason.TIMEOUT)
            throw ProximityException(
                ProximityError.Transport("ble_inactivity_timeout", "The BLE peer made no write progress for 30 seconds")
            )
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { terminate(ProximityCloseReason.CANCELLED) }
            throw cancelled
        } catch (failure: ProximityException) {
            val reason = if (failure.error is ProximityError.Protocol) {
                ProximityCloseReason.PROTOCOL_ERROR
            } else {
                ProximityCloseReason.PEER_DISCONNECTED
            }
            withContext(NonCancellable) { terminate(reason) }
            throw failure
        } catch (failure: Throwable) {
            withContext(NonCancellable) { terminate(ProximityCloseReason.PEER_DISCONNECTED) }
            throw normalizedTransportFailure(
                failure,
                code = "ble_send_failed",
                message = "The BLE bearer failed while sending data",
            )
        }
    }

    override suspend fun close(reason: ProximityCloseReason) = withContext(NonCancellable) {
        sendMutex.withLock {
            val shouldClose = closeMutex.withLock {
                if (closed) false else {
                    closed = true
                    true
                }
            }
            if (shouldClose) {
                var finalReason = reason
                try {
                    if (reason == ProximityCloseReason.COMPLETED) {
                        try {
                            withTimeout(BLE_INACTIVITY_TIMEOUT) { raw.finish() }
                        } catch (_: TimeoutCancellationException) {
                            finalReason = ProximityCloseReason.TIMEOUT
                            throw ProximityException(
                                ProximityError.Transport(
                                    "ble_inactivity_timeout",
                                    "The BLE peer made no finish progress for 30 seconds",
                                )
                            )
                        } catch (cancelled: CancellationException) {
                            finalReason = ProximityCloseReason.CANCELLED
                            throw cancelled
                        } catch (failure: ProximityException) {
                            finalReason = if (failure.error is ProximityError.Protocol) {
                                ProximityCloseReason.PROTOCOL_ERROR
                            } else {
                                ProximityCloseReason.PEER_DISCONNECTED
                            }
                            throw failure
                        } catch (failure: Throwable) {
                            finalReason = ProximityCloseReason.PEER_DISCONNECTED
                            throw normalizedTransportFailure(
                                failure,
                                code = "ble_finish_failed",
                                message = "The BLE bearer failed while finishing the session",
                            )
                        }
                    }
                } finally {
                    raw.close(finalReason)
                }
            }
        }
    }

    private suspend fun terminate(reason: ProximityCloseReason) {
        val shouldClose = closeMutex.withLock {
            if (closed) false else {
                closed = true
                true
            }
        }
        if (shouldClose) raw.close(reason)
    }

    private companion object {
        val BLE_INACTIVITY_TIMEOUT = 30.seconds
    }
}

private fun normalizedTransportFailure(failure: Throwable, code: String, message: String): ProximityException =
    if (failure is ProximityException) failure
    else ProximityException(ProximityError.Transport(code, message), failure)
