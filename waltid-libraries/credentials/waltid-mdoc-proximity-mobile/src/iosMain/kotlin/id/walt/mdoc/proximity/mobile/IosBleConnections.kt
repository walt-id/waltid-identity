@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.proximity.ProximityCloseReason
import id.walt.mdoc.proximity.ProximityError
import id.walt.mdoc.proximity.ProximityException
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.CoreBluetooth.CBL2CAPChannel
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Foundation.NSStreamStatusAtEnd
import platform.Foundation.NSStreamStatusClosed
import platform.Foundation.NSStreamStatusError
import platform.posix.memcpy
import kotlin.coroutines.coroutineContext

internal fun iosTransportFailure(code: String, message: String, cause: Throwable? = null) =
    ProximityException(ProximityError.Transport(code, message), cause)

internal fun ByteArray.toNSData(): NSData = if (isEmpty()) NSData() else usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
}

internal fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).also { bytes ->
    if (bytes.isNotEmpty()) bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), this.bytes, length) }
}

internal class IosL2capConnection(
    private val channel: CBL2CAPChannel,
    scope: CoroutineScope,
    private val onClose: () -> Unit = {},
) : BleRawConnection {
    override val bearer: BleRawBearer = BleRawBearer.L2CAP
    override val maximumGattPacketBytes: Int? = null
    private val closed = atomic(false)
    private val packets = Channel<ByteArray>(Channel.BUFFERED)
    override val incoming = packets
    private val writeMutex = Mutex()
    private val reader: Job

    init {
        val input = channel.inputStream
        val output = channel.outputStream
        if (input == null || output == null) {
            closed.value = true
            packets.close(iosTransportFailure("ble_l2cap_stream_missing", "CoreBluetooth returned an incomplete L2CAP channel"))
        } else {
            input.open()
            output.open()
        }
        reader = scope.launch(Dispatchers.Default) { readLoop() }
    }

    private suspend fun readLoop() {
        val input = channel.inputStream ?: return
        val buffer = ByteArray(16 * 1024)
        try {
            while (!closed.value) {
                coroutineContext.ensureActive()
                when (input.streamStatus) {
                    NSStreamStatusAtEnd, NSStreamStatusClosed -> break
                    NSStreamStatusError -> throw iosTransportFailure(
                        "ble_l2cap_read_failed",
                        input.streamError?.localizedDescription ?: "The CoreBluetooth L2CAP stream failed",
                    )
                }
                if (!input.hasBytesAvailable) {
                    delay(10)
                    continue
                }
                val count = buffer.usePinned { pinned ->
                    input.read(pinned.addressOf(0).reinterpret<UByteVar>(), buffer.size.toULong()).toInt()
                }
                if (count < 0) throw iosTransportFailure(
                    "ble_l2cap_read_failed",
                    input.streamError?.localizedDescription ?: "The CoreBluetooth L2CAP read failed",
                )
                if (count == 0) {
                    delay(10)
                } else {
                    packets.send(buffer.copyOf(count))
                }
            }
            packets.close()
        } catch (failure: Throwable) {
            if (closed.value) packets.close() else packets.close(failure)
        }
    }

    override suspend fun write(bytes: ByteArray) = writeMutex.withLock {
        val output = channel.outputStream ?: throw iosTransportFailure(
            "ble_l2cap_stream_missing",
            "The CoreBluetooth L2CAP output stream is unavailable",
        )
        withContext(Dispatchers.Default) {
            var offset = 0
            while (offset < bytes.size) {
                coroutineContext.ensureActive()
                when (output.streamStatus) {
                    NSStreamStatusAtEnd, NSStreamStatusClosed, NSStreamStatusError -> throw iosTransportFailure(
                        "ble_l2cap_write_failed",
                        output.streamError?.localizedDescription ?: "The CoreBluetooth L2CAP stream is closed",
                    )
                }
                if (!output.hasSpaceAvailable) {
                    delay(10)
                    continue
                }
                // Bound individual writes: some CoreBluetooth versions corrupt very large writes.
                val count = minOf(512, bytes.size - offset)
                val written = bytes.usePinned { pinned ->
                    output.write(
                        pinned.addressOf(offset).reinterpret<UByteVar>(),
                        count.toULong(),
                    ).toInt()
                }
                if (written < 0) throw iosTransportFailure(
                    "ble_l2cap_write_failed",
                    output.streamError?.localizedDescription ?: "The CoreBluetooth L2CAP write failed",
                )
                if (written == 0) delay(10) else offset += written
            }
        }
    }

    override suspend fun finish() = Unit

    override fun close(reason: ProximityCloseReason) {
        if (!closed.compareAndSet(false, true)) return
        reader.cancel()
        channel.inputStream?.close()
        channel.outputStream?.close()
        packets.close()
        onClose()
    }
}
