package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.proximity.ProximityCloseReason
import kotlinx.coroutines.CoroutineScope

internal interface WifiAwarePlatformAdapter {
    suspend fun capability(): WifiAwareProximityAvailability

    suspend fun preparePublisher(
        serviceName: String,
        passphrase: String,
        sessionScope: CoroutineScope,
    ): WifiAwarePreparedPlatformPublisher
}

internal interface WifiAwarePreparedPlatformPublisher {
    val supportedBands: WifiAwareSupportedBands

    suspend fun awaitConnection(): WifiAwareRawConnection

    fun close(reason: ProximityCloseReason)
}

/** Platform-owned TCP stream fragments with suspendable writes and deterministic close. */
internal interface WifiAwareRawConnection {
    /** Signals platform-observed or local closure without reading from the socket. */
    suspend fun awaitClosed(): ProximityCloseReason

    /** Reads at most [maximumBytes], returns an empty array only when no bytes are currently possible. */
    suspend fun read(maximumBytes: Int): ByteArray?

    suspend fun write(bytes: ByteArray)

    fun close(reason: ProximityCloseReason)
}
