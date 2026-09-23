@file:OptIn(ExperimentalSerializationApi::class)

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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.time.Duration.Companion.seconds

internal class DefaultWifiAwareProximityTransportProvider(
    private val configuration: WifiAwareProximityTransportConfiguration,
    private val platform: WifiAwarePlatformAdapter,
) : ReaderSelectedTransportProvider {
    override val kind: ProximityTransportKind = ProximityTransportKind.WIFI_AWARE
    private val prepared = atomic(false)

    override suspend fun capability(context: EngagementContext): ProximityCapability {
        val availability = platform.capability()
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
            require(method.operatingClass == null && method.channelNumber == null && method.extensions.isEmpty()) {
                "The Wi-Fi Aware platform does not support reader-selected channels or extensions"
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
        check(prepared.compareAndSet(expect = false, update = true)) {
            "A Wi-Fi Aware provider owns one engagement endpoint; use independent keys for concurrent engagements"
        }
        var acquired: WifiAwarePreparedPlatformPublisher? = null
        val publication = try {
            withTimeout(PREPARE_TIMEOUT) {
                platform.preparePublisher(
                    serviceName = serviceName,
                    passphrase = passphrase,
                    sessionScope = sessionScope,
                ).also { acquired = it }
            }
        } catch (_: TimeoutCancellationException) {
            acquired?.close(ProximityCloseReason.TIMEOUT)
            throw ProximityException(
                ProximityError.Transport(
                    "wifi_aware_prepare_timeout",
                    "Wi-Fi Aware publisher preparation timed out",
                )
            )
        } catch (cancelled: CancellationException) {
            acquired?.close(ProximityCloseReason.CANCELLED)
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
            return PreparedWifiAwareTransport(publication, method, context.maximumMessageBytes)
        } catch (failure: Throwable) {
            withContext(NonCancellable) { publication.close(ProximityCloseReason.CANCELLED) }
            throw failure
        }
    }

    private companion object {
        val PREPARE_TIMEOUT = 30.seconds
    }
}

/** One publisher and one accept owner, belonging to exactly one engagement. */
private class PreparedWifiAwareTransport(
    private val platform: WifiAwarePreparedPlatformPublisher,
    connectionMethod: DeviceRetrievalMethod.WifiAware,
    private val maximumMessageBytes: Int,
) : PreparedTransport {
    private val method = ReaderSelectedTransportOffer.Method(connectionMethod)
    override val connectionMethod: DeviceRetrievalMethod get() = method.value
    override val kind: ProximityTransportKind = ProximityTransportKind.WIFI_AWARE
    private val closed = atomic(false)
    private val awaited = atomic(false)
    private val connection = atomic<ProximityConnection?>(null)

    override suspend fun awaitConnection(): ProximityConnection {
        check(awaited.compareAndSet(expect = false, update = true)) {
            "A prepared Wi-Fi Aware endpoint accepts one connection"
        }
        check(!closed.value) { "Wi-Fi Aware endpoint is closed" }
        var acquired: WifiAwareRawConnection? = null
        try {
            val raw = withTimeout(CONNECTION_TIMEOUT) {
                platform.awaitConnection().also { acquired = it }
            }
            val exact = WifiAwareHttpConnection(raw, maximumMessageBytes)
            connection.value = exact
            if (closed.value) {
                connection.compareAndSet(expect = exact, update = null)
                exact.close(ProximityCloseReason.CANCELLED)
                throw CancellationException("Wi-Fi Aware endpoint closed during connection delivery")
            }
            return exact
        } catch (failure: Throwable) {
            val reason = if (failure is TimeoutCancellationException) ProximityCloseReason.TIMEOUT
                else ProximityCloseReason.CANCELLED
            withContext(NonCancellable) {
                acquired?.close(reason)
                close(reason)
            }
            if (failure is TimeoutCancellationException) throw ProximityException(
                ProximityError.Transport("wifi_aware_connection_timeout", "Wi-Fi Aware reader connection timed out")
            )
            throw failure
        }
    }

    override suspend fun close(reason: ProximityCloseReason) {
        if (closed.compareAndSet(expect = false, update = true)) {
            // Close native resources before waiting for any framed connection cleanup.
            platform.close(reason)
            connection.getAndSet(null)?.close(reason)
        }
    }

    private companion object {
        val CONNECTION_TIMEOUT = 60.seconds
    }
}
