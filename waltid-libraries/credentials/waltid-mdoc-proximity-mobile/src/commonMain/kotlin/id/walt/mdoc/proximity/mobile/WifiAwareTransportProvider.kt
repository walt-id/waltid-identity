@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.objects.engagement.DeviceRetrievalMethod
import id.walt.mdoc.proximity.EngagementContext
import id.walt.mdoc.proximity.MdocEngagementMode
import id.walt.mdoc.proximity.PreparedTransport
import id.walt.mdoc.proximity.ProximityCapability
import id.walt.mdoc.proximity.ProximityCloseReason
import id.walt.mdoc.proximity.ProximityConnection
import id.walt.mdoc.proximity.ProximityError
import id.walt.mdoc.proximity.ProximityException
import id.walt.mdoc.proximity.ProximityTransportKind
import id.walt.mdoc.proximity.ReaderSelectedTransportOffer
import id.walt.mdoc.proximity.ReaderSelectedTransportProvider
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds

internal class DefaultWifiAwareProximityTransportProvider(
    private val configuration: WifiAwareProximityTransportConfiguration,
    private val platform: WifiAwarePlatformAdapter,
) : ReaderSelectedTransportProvider {
    override val kind: ProximityTransportKind = ProximityTransportKind.WIFI_AWARE
    private val prepareMutex = Mutex()
    private var sharedPublication: SharedWifiAwarePublication? = null

    override suspend fun capability(context: EngagementContext): ProximityCapability {
        val availability = platform.capability(configuration.securityPolicy)
        val unavailable = availability as? WifiAwareProximityAvailability.Unavailable
        return ProximityCapability(
            implemented = unavailable?.implemented ?: true,
            profilePermitted = true,
            runtimeAvailable = availability is WifiAwareProximityAvailability.Available,
            sessionSelected = availability is WifiAwareProximityAvailability.Available,
            unavailableReason = unavailable?.let {
                ProximityError.Capability(it.code, it.message)
            },
        )
    }

    override suspend fun prepare(
        context: EngagementContext,
        sessionScope: CoroutineScope,
    ): PreparedTransport = prepareInternal(
        context = context,
        sessionScope = sessionScope,
        readerBands = null,
    )

    override fun acceptsReaderOffer(offer: ReaderSelectedTransportOffer): Boolean {
        val method = (offer as? ReaderSelectedTransportOffer.Method)?.value as? DeviceRetrievalMethod.WifiAware
            ?: return false
        return runCatching {
            require(method.passphraseInfo == null) {
                "A Wi-Fi Aware reader offer must not select the holder passphrase"
            }
            WifiAwareSupportedBands.fromBytes(method.supportedBands)
        }.isSuccess
    }

    override suspend fun prepareReaderSelected(
        offer: ReaderSelectedTransportOffer,
        context: EngagementContext,
        sessionScope: CoroutineScope,
    ): PreparedTransport {
        require(context.engagementMode == MdocEngagementMode.Nfc) {
            "A Wi-Fi Aware NFC carrier can only be selected during NFC engagement"
        }
        require(acceptsReaderOffer(offer)) { "The Wi-Fi Aware reader offer is invalid or unsupported" }
        val method = (offer as ReaderSelectedTransportOffer.Method).value as DeviceRetrievalMethod.WifiAware
        return prepareInternal(
            context = context,
            sessionScope = sessionScope,
            readerBands = WifiAwareSupportedBands.fromBytes(method.supportedBands),
        )
    }

    private suspend fun prepareInternal(
        context: EngagementContext,
        sessionScope: CoroutineScope,
        readerBands: WifiAwareSupportedBands?,
    ): PreparedTransport {
        val capability = capability(context)
        if (!capability.mayPrepare) {
            throw ProximityException(
                capability.unavailableReason ?: ProximityError.Capability(
                    "wifi_aware_unavailable",
                    "Wi-Fi Aware is unavailable",
                )
            )
        }
        val serviceName = WifiAwareProtocol.deriveServiceName(configuration.eDeviceKeyBytes)
        val passphrase = WifiAwareProtocol.derivePassphrase(configuration.eDeviceKeyBytes)
        val publication = try {
            prepareMutex.withLock {
                sharedPublication ?: SharedWifiAwarePublication(
                    platform = withTimeout(PREPARE_TIMEOUT) {
                        platform.preparePublisher(
                            serviceName = serviceName,
                            passphrase = passphrase,
                            securityPolicy = configuration.securityPolicy,
                            sessionScope = sessionScope,
                        )
                    },
                    maximumMessageBytes = context.maximumMessageBytes,
                    sessionJob = sessionScope.coroutineContext[Job],
                ).also { sharedPublication = it }
            }
        } catch (_: TimeoutCancellationException) {
            throw ProximityException(
                ProximityError.Transport(
                    "wifi_aware_prepare_timeout",
                    "Wi-Fi Aware publisher preparation timed out",
                )
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: ProximityException) {
            throw failure
        } catch (failure: Throwable) {
            throw ProximityException(
                ProximityError.Transport(
                    "wifi_aware_prepare_failed",
                    "Wi-Fi Aware publisher preparation failed",
                ),
                failure,
            )
        }
        try {
            val bands = readerBands?.let(publication.supportedBands::intersect) ?: publication.supportedBands
            val method = DeviceRetrievalMethod.WifiAware(
                passphraseInfo = passphrase.takeIf { context.engagementMode == MdocEngagementMode.Nfc },
                supportedBands = bands.encoded(),
            )
            return publication.acquire(method)
        } catch (failure: Throwable) {
            withContext(NonCancellable) { publication.forceClose(ProximityCloseReason.CANCELLED) }
            throw failure
        }
    }

    private companion object {
        val PREPARE_TIMEOUT = 30.seconds
    }
}

private class SharedWifiAwarePublication(
    private val platform: WifiAwarePreparedPlatformPublisher,
    private val maximumMessageBytes: Int,
    sessionJob: Job?,
) {
    val supportedBands: WifiAwareSupportedBands get() = platform.supportedBands
    private val closed = atomic(false)
    private val awaited = atomic(false)
    private val connection = atomic<ProximityConnection?>(null)
    private val referenceMutex = Mutex()
    private var references = 0
    private var sessionHandle: kotlinx.coroutines.DisposableHandle? = null

    init {
        sessionHandle = sessionJob?.invokeOnCompletion {
            forceClose(ProximityCloseReason.CANCELLED)
        }
    }

    suspend fun acquire(method: DeviceRetrievalMethod.WifiAware): PreparedTransport = referenceMutex.withLock {
        check(!closed.value) { "Wi-Fi Aware publication is closed" }
        references++
        PreparedWifiAwareTransport(this, method)
    }

    suspend fun awaitConnection(): ProximityConnection {
        check(awaited.compareAndSet(expect = false, update = true)) {
            "A prepared Wi-Fi Aware endpoint accepts one connection"
        }
        ensureOpen()
        return try {
            val raw = withTimeout(CONNECTION_TIMEOUT) { platform.awaitConnection() }
            if (closed.value) {
                raw.close(ProximityCloseReason.CANCELLED)
                throw CancellationException("Wi-Fi Aware endpoint closed while accepting a connection")
            }
            WifiAwareHttpConnection(raw, maximumMessageBytes).also { exact ->
                connection.value = exact
                if (closed.value && connection.compareAndSet(expect = exact, update = null)) {
                    exact.close(ProximityCloseReason.CANCELLED)
                }
            }
        } catch (_: TimeoutCancellationException) {
            forceClose(ProximityCloseReason.TIMEOUT)
            throw ProximityException(
                ProximityError.Transport(
                    "wifi_aware_connection_timeout",
                    "Wi-Fi Aware reader connection timed out",
                )
            )
        }
    }

    suspend fun release(reason: ProximityCloseReason) {
        val close = referenceMutex.withLock {
            if (references == 0) false else {
                references--
                references == 0
            }
        }
        if (close) {
            connection.getAndSet(null)?.close(reason)
            forceClose(reason)
        }
    }

    fun forceClose(reason: ProximityCloseReason) {
        if (closed.compareAndSet(expect = false, update = true)) {
            sessionHandle?.dispose()
            platform.close(reason)
        }
    }

    private fun ensureOpen() = check(!closed.value) { "Wi-Fi Aware endpoint is closed" }

    private companion object {
        val CONNECTION_TIMEOUT = 60.seconds
    }
}

private class PreparedWifiAwareTransport(
    private val shared: SharedWifiAwarePublication,
    override val connectionMethod: DeviceRetrievalMethod.WifiAware,
) : PreparedTransport {
    override val kind: ProximityTransportKind = ProximityTransportKind.WIFI_AWARE
    private val closed = atomic(false)

    override suspend fun awaitConnection(): ProximityConnection {
        check(!closed.value) { "Wi-Fi Aware transport handle is closed" }
        return shared.awaitConnection()
    }

    override suspend fun close(reason: ProximityCloseReason) {
        if (closed.compareAndSet(expect = false, update = true)) shared.release(reason)
    }
}
