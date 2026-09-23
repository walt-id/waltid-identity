package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.proximity.ProximityError
import id.walt.mdoc.proximity.ProximityException
import kotlinx.coroutines.channels.Channel

/** Callback producers cannot suspend. A live capacity failure terminates the bearer explicitly. */
internal fun Channel<ByteArray>.offerBlePacket(bytes: ByteArray, closeBearer: () -> Unit): Boolean {
    val result = trySend(bytes.copyOf())
    if (result.isSuccess) return true
    if (result.isClosed) return false
    val failure = ProximityException(
        ProximityError.Transport("ble_receive_overflow", "The BLE receive buffer capacity was exceeded"),
    )
    if (close(failure)) {
        // Discard buffered fragments so a partial message cannot continue after transport failure.
        while (tryReceive().isSuccess) { /* discard incomplete payload */ }
        closeBearer()
    }
    return false
}
