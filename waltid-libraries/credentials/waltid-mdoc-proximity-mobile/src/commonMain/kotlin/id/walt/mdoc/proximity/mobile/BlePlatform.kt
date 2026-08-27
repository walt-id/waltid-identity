package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.proximity.ProximityCloseReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel

internal enum class BlePlatformRole { CENTRAL_CLIENT, PERIPHERAL_SERVER }
internal enum class BleRawBearer { GATT, L2CAP }

internal interface BlePlatformAdapter {
    suspend fun capability(): BleProximityAvailability

    suspend fun prepareCentralClient(
        serviceUuid: BleServiceUuid,
        expectedIdent: ByteArray,
        preferL2cap: Boolean,
        sessionScope: CoroutineScope,
    ): BlePreparedPlatformRole

    suspend fun preparePeripheralServer(
        serviceUuid: BleServiceUuid,
        preferL2cap: Boolean,
        sessionScope: CoroutineScope,
    ): BlePreparedPlatformRole
}

internal interface BlePreparedPlatformRole {
    val role: BlePlatformRole
    val serviceUuid: BleServiceUuid
    val l2capPsm: UInt?

    suspend fun awaitConnection(): BleRawConnection
    fun close(reason: ProximityCloseReason)
}

/** Platform-owned GATT packets or L2CAP stream fragments with suspendable write backpressure. */
internal interface BleRawConnection {
    val bearer: BleRawBearer
    val incoming: ReceiveChannel<ByteArray>

    /** Current maximum complete GATT characteristic value, including the ISO continuation marker. */
    val maximumGattPacketBytes: Int?

    suspend fun write(bytes: ByteArray)
    suspend fun finish()
    fun close(reason: ProximityCloseReason)
}
