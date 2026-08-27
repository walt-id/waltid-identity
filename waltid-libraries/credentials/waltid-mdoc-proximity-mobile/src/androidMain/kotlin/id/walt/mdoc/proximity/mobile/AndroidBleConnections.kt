package id.walt.mdoc.proximity.mobile

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothSocket
import android.bluetooth.BluetoothStatusCodes
import android.os.Build
import id.walt.mdoc.proximity.ProximityCloseReason
import id.walt.mdoc.proximity.ProximityError
import id.walt.mdoc.proximity.ProximityException
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class AndroidL2capConnection(
    private val socket: BluetoothSocket,
    parentScope: CoroutineScope,
) : BleRawConnection {
    override val bearer: BleRawBearer = BleRawBearer.L2CAP
    override val maximumGattPacketBytes: Int? = null
    private val closed = AtomicBoolean(false)
    private val writeMutex = Mutex()
    private val packets = Channel<ByteArray>(Channel.BUFFERED)
    override val incoming = packets
    private val readerJob: Job = parentScope.launch(Dispatchers.IO) {
        val buffer = ByteArray(16 * 1024)
        try {
            while (true) {
                val count = socket.inputStream.read(buffer)
                if (count < 0) break
                if (count > 0) packets.send(buffer.copyOf(count))
            }
            packets.close()
        } catch (failure: IOException) {
            if (closed.get()) packets.close() else packets.close(failure)
        }
    }

    override suspend fun write(bytes: ByteArray) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            socket.outputStream.write(bytes)
            socket.outputStream.flush()
        }
    }

    override suspend fun finish() = Unit

    override fun close(reason: ProximityCloseReason) {
        if (!closed.compareAndSet(false, true)) return
        runCatching { socket.close() }
        readerJob.cancel()
        packets.close()
    }
}

internal sealed interface AndroidGattOperation {
    data class Connected(val status: Int) : AndroidGattOperation
    data class MtuChanged(val mtu: Int, val status: Int) : AndroidGattOperation
    data class ServicesDiscovered(val status: Int) : AndroidGattOperation
    data class CharacteristicRead(val uuid: java.util.UUID, val value: ByteArray, val status: Int) : AndroidGattOperation
    data class CharacteristicWrite(val uuid: java.util.UUID, val status: Int) : AndroidGattOperation
    data class DescriptorWrite(val uuid: java.util.UUID, val status: Int) : AndroidGattOperation
    data class NotificationSent(val status: Int) : AndroidGattOperation
    data class Disconnected(val status: Int) : AndroidGattOperation
}

internal fun androidTransportFailure(code: String, message: String, cause: Throwable? = null) =
    ProximityException(ProximityError.Transport(code, message), cause)

internal fun requireGattSuccess(status: Int, operation: String) {
    if (status != BluetoothGatt.GATT_SUCCESS) throw androidTransportFailure(
        "android_gatt_failure",
        "Android GATT $operation failed with status $status",
    )
}

@SuppressLint("MissingPermission")
internal fun BluetoothGatt.writeWithoutResponse(
    characteristic: BluetoothGattCharacteristic,
    value: ByteArray,
): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) == BluetoothStatusCodes.SUCCESS
} else {
    @Suppress("DEPRECATION")
    characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
    @Suppress("DEPRECATION")
    characteristic.value = value
    @Suppress("DEPRECATION")
    writeCharacteristic(characteristic)
}

@SuppressLint("MissingPermission")
internal fun BluetoothGatt.writeDescriptorCompat(
    descriptor: BluetoothGattDescriptor,
    value: ByteArray,
): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
} else {
    @Suppress("DEPRECATION")
    descriptor.value = value
    @Suppress("DEPRECATION")
    writeDescriptor(descriptor)
}

@SuppressLint("MissingPermission")
internal fun BluetoothGattServer.notifyCompat(
    device: android.bluetooth.BluetoothDevice,
    characteristic: BluetoothGattCharacteristic,
    value: ByteArray,
): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    notifyCharacteristicChanged(device, characteristic, false, value) == BluetoothStatusCodes.SUCCESS
} else {
    @Suppress("DEPRECATION")
    characteristic.value = value
    @Suppress("DEPRECATION")
    notifyCharacteristicChanged(device, characteristic, false)
}
