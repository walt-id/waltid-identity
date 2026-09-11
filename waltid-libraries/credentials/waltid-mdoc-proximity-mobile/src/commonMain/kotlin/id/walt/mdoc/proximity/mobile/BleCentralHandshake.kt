package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.proximity.ProximityError
import id.walt.mdoc.proximity.ProximityException

internal data class BleReaderCharacteristics<C>(
    val state: C,
    val clientToServer: C,
    val serverToClient: C,
    val ident: C,
    val psm: C?,
)

/** Ordered ISO central-client setup; native discovery and resource ownership stay in the adapters. */
internal suspend fun <C> establishBleCentralBearer(
    characteristics: BleReaderCharacteristics<C>,
    expectedIdent: ByteArray,
    preferL2cap: Boolean,
    read: suspend (C) -> ByteArray,
    subscribe: suspend (C) -> Unit,
    write: suspend (C, ByteArray) -> Unit,
    openL2cap: suspend (UInt) -> BleRawConnection?,
    gatt: () -> BleRawConnection,
): BleRawConnection {
    if (!BleIdent.matches(expectedIdent, read(characteristics.ident))) throw ProximityException(
        ProximityError.Security("ble_ident_mismatch", "The reader BLEIdent does not match EDeviceKeyBytes"),
    )
    if (preferL2cap && characteristics.psm != null) {
        openL2cap(BlePsmCodec.decode(read(characteristics.psm)))?.let { return it }
    }
    subscribe(characteristics.state)
    subscribe(characteristics.serverToClient)
    write(characteristics.state, byteArrayOf(BLE_STATE_START))
    return gatt()
}
