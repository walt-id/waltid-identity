@file:Suppress("DEPRECATION")

package id.walt.mdoc.proximity.mobile

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothSocket
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import id.walt.mdoc.proximity.ProximityCloseReason
import id.walt.mdoc.proximity.ProximityError
import id.walt.mdoc.proximity.ProximityException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

@SuppressLint("MissingPermission")
internal class AndroidBleCentralRole(
    private val context: Context,
    private val adapter: BluetoothAdapter,
    override val serviceUuid: BleServiceUuid,
    private val expectedIdent: ByteArray,
    private val preferL2cap: Boolean,
    private val sessionScope: CoroutineScope,
) : BlePreparedPlatformRole {
    override val role: BlePlatformRole = BlePlatformRole.CENTRAL_CLIENT
    override val l2capPsm: UInt? = null
    private val pendingSocket = AtomicReference<BlockingSocket<BluetoothSocket>?>(null)
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    @Volatile private var scanCallback: ScanCallback? = null
    @Volatile private var gattSession: AndroidCentralGattSession? = null
    @Volatile private var connection: BleRawConnection? = null
    private val completion: DisposableHandle? = sessionScope.coroutineContext[Job]?.invokeOnCompletion {
        close(ProximityCloseReason.CANCELLED)
    }

    override suspend fun awaitConnection(): BleRawConnection {
        check(started.compareAndSet(false, true)) { "A prepared BLE central-client role can be awaited only once" }
        return try {
            val device = scanForReader()
            val session = AndroidCentralGattSession(context, device).also { gattSession = it }
            if (closed.get()) {
                session.close()
                throw CancellationException("BLE role is closed")
            }
            session.connect()
            val established = establishBearer(session)
            connection = established
            if (closed.get()) {
                established.close(ProximityCloseReason.CANCELLED)
                throw CancellationException("BLE role is closed")
            }
            established
        } catch (cancelled: CancellationException) {
            close(ProximityCloseReason.CANCELLED)
            throw cancelled
        } catch (failure: Throwable) {
            close(ProximityCloseReason.CANCELLED)
            throw failure
        }
    }

    private suspend fun scanForReader(): BluetoothDevice = suspendCancellableCoroutine { continuation ->
        val scanner = adapter.bluetoothLeScanner
            ?: return@suspendCancellableCoroutine continuation.resumeWithException(
                androidTransportFailure("ble_scanner_unavailable", "The Android BLE scanner is unavailable")
            )
        val delivered = AtomicBoolean(false)
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (!delivered.compareAndSet(false, true)) return
                scanner.stopScan(this)
                scanCallback = null
                continuation.resume(result.device)
            }

            override fun onScanFailed(errorCode: Int) {
                if (!delivered.compareAndSet(false, true)) return
                scanCallback = null
                continuation.resumeWithException(
                    androidTransportFailure("ble_scan_failed", "Android BLE scan failed with error $errorCode")
                )
            }
        }
        scanCallback = callback
        continuation.invokeOnCancellation {
            if (delivered.compareAndSet(false, true)) runCatching { scanner.stopScan(callback) }
            scanCallback = null
        }
        scanner.startScan(
            listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString(serviceUuid.platformString()))).build()),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            callback,
        )
        if (!continuation.isActive || closed.get()) runCatching { scanner.stopScan(callback) }
    }

    private suspend fun establishBearer(session: AndroidCentralGattSession): BleRawConnection {
        val service = session.discover(UUID.fromString(serviceUuid.platformString()))
        val characteristics = BleReaderCharacteristics(
            state = service.required(BleGattUuid.READER_STATE),
            clientToServer = service.required(BleGattUuid.READER_CLIENT_TO_SERVER),
            serverToClient = service.required(BleGattUuid.READER_SERVER_TO_CLIENT),
            ident = service.required(BleGattUuid.READER_IDENT),
            psm = service.getCharacteristic(UUID.fromString(BleGattUuid.READER_L2CAP_PSM)),
        )
        return establishBleCentralBearer(
            characteristics, expectedIdent, preferL2cap,
            read = session::read,
            subscribe = session::subscribe,
            write = session::write,
            openL2cap = { psm ->
                openL2cap(session.device, psm.toInt())?.let { socket ->
                    session.close()
                    gattSession = null
                    AndroidL2capConnection(socket, sessionScope)
                }
            },
            gatt = { AndroidCentralGattConnection(session, characteristics.state, characteristics.clientToServer) },
        )
    }

    private suspend fun openL2cap(device: BluetoothDevice, psm: Int): BlockingSocket<BluetoothSocket>? {
        val socket = runCatching { BlockingSocket(device.createInsecureL2capChannel(psm)) }.getOrNull() ?: return null
        pendingSocket.set(socket)
        try {
            if (closed.get()) throw CancellationException("BLE role is closed")
            val connected = withTimeoutOrNull(5.seconds) { socket.run { it.connect() }; true } == true
            if (!connected) socket.close()
            return socket.takeIf { connected }
        } catch (cancelled: CancellationException) {
            socket.close()
            throw cancelled
        } catch (_: Exception) {
            socket.close()
            return null
        } finally {
            pendingSocket.compareAndSet(socket, null)
        }
    }

    override fun close(reason: ProximityCloseReason) {
        if (!closed.compareAndSet(false, true)) return
        completion?.dispose()
        scanCallback?.let { callback -> runCatching { adapter.bluetoothLeScanner?.stopScan(callback) } }
        scanCallback = null
        pendingSocket.getAndSet(null)?.close()
        connection?.close(reason)
        gattSession?.close()
    }
}

private fun BluetoothGattService.required(uuid: String): BluetoothGattCharacteristic =
    getCharacteristic(UUID.fromString(uuid)) ?: throw androidTransportFailure(
        "ble_characteristic_missing",
        "The reader BLE service is missing required characteristic $uuid",
    )

@SuppressLint("MissingPermission")
private class AndroidCentralGattSession(
    private val context: Context,
    val device: BluetoothDevice,
) {
    val operations = Channel<AndroidGattOperation>(Channel.UNLIMITED)
    val incoming = Channel<ByteArray>(Channel.BUFFERED)
    private val closed = AtomicBoolean(false)
    @Volatile var mtu: Int = 23
        private set
    private lateinit var gatt: BluetoothGatt
    private val ownedGatt = AtomicReference<BluetoothGatt?>(null)

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> operations.trySend(AndroidGattOperation.Connected(status))
                BluetoothProfile.STATE_DISCONNECTED -> {
                    operations.trySend(AndroidGattOperation.Disconnected(status))
                    incoming.close(
                        if (status == BluetoothGatt.GATT_SUCCESS) null
                        else androidTransportFailure(
                            "android_gatt_failure",
                            "The Android BLE peer disconnected with status $status",
                        )
                    )
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            operations.trySend(AndroidGattOperation.MtuChanged(mtu, status))
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            operations.trySend(AndroidGattOperation.ServicesDiscovered(status))
        }

        @Deprecated("Deprecated in Android 13")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            operations.trySend(AndroidGattOperation.CharacteristicRead(characteristic.uuid, characteristic.value ?: ByteArray(0), status))
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            operations.trySend(AndroidGattOperation.CharacteristicRead(characteristic.uuid, value.copyOf(), status))
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            operations.trySend(AndroidGattOperation.CharacteristicWrite(characteristic.uuid, status))
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            operations.trySend(AndroidGattOperation.DescriptorWrite(descriptor.uuid, status))
        }

        @Deprecated("Deprecated in Android 13")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleNotification(characteristic.uuid, characteristic.value?.copyOf() ?: ByteArray(0))
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleNotification(characteristic.uuid, value.copyOf())
        }
    }

    private fun handleNotification(uuid: UUID, value: ByteArray) {
        when (uuid) {
            UUID.fromString(BleGattUuid.READER_SERVER_TO_CLIENT) -> incoming.offerBlePacket(value, ::close)
            UUID.fromString(BleGattUuid.READER_STATE) -> when {
                value.contentEquals(byteArrayOf(BLE_STATE_END)) -> incoming.close()
                else -> incoming.close(
                    ProximityException(
                        ProximityError.Protocol("invalid_ble_state", "The reader sent an invalid BLE state value")
                    )
                )
            }
        }
    }

    suspend fun connect() {
        if (closed.get()) throw CancellationException("GATT session is closed")
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            ?: throw androidTransportFailure("ble_connect_failed", "Android could not create a GATT connection")
        ownedGatt.set(gatt)
        if (closed.get()) {
            closeGatt()
            throw CancellationException("GATT session is closed")
        }
        val connected = awaitOperation<AndroidGattOperation.Connected>()
        requireGattSuccess(connected.status, "connection")
        if (gatt.requestMtu(515)) {
            val result = awaitOperation<AndroidGattOperation.MtuChanged>()
            if (result.status == BluetoothGatt.GATT_SUCCESS) mtu = result.mtu
        }
    }

    suspend fun discover(serviceUuid: UUID): BluetoothGattService {
        if (!gatt.discoverServices()) throw androidTransportFailure("ble_discovery_failed", "Android rejected service discovery")
        requireGattSuccess(awaitOperation<AndroidGattOperation.ServicesDiscovered>().status, "service discovery")
        return gatt.getService(serviceUuid) ?: throw androidTransportFailure(
            "ble_service_missing",
            "The discovered reader does not expose the requested BLE service",
        )
    }

    suspend fun read(characteristic: BluetoothGattCharacteristic): ByteArray {
        if (!gatt.readCharacteristic(characteristic)) throw androidTransportFailure(
            "ble_read_failed",
            "Android rejected a BLE characteristic read",
        )
        val result = awaitOperation<AndroidGattOperation.CharacteristicRead> { it.uuid == characteristic.uuid }
        requireGattSuccess(result.status, "characteristic read")
        return result.value
    }

    suspend fun subscribe(characteristic: BluetoothGattCharacteristic) {
        if (!gatt.setCharacteristicNotification(characteristic, true)) throw androidTransportFailure(
            "ble_subscribe_failed",
            "Android rejected BLE notification subscription",
        )
        val descriptorUuid = UUID.fromString(BleGattUuid.CLIENT_CHARACTERISTIC_CONFIGURATION)
        val descriptor = characteristic.getDescriptor(descriptorUuid) ?: throw androidTransportFailure(
            "ble_descriptor_missing",
            "A required BLE notification characteristic has no CCCD",
        )
        if (!gatt.writeDescriptorCompat(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
            throw androidTransportFailure("ble_subscribe_failed", "Android rejected the BLE CCCD write")
        }
        val result = awaitOperation<AndroidGattOperation.DescriptorWrite> { it.uuid == descriptorUuid }
        requireGattSuccess(result.status, "CCCD write")
    }

    suspend fun write(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        if (!gatt.writeWithoutResponse(characteristic, value)) throw androidTransportFailure(
            "ble_write_failed",
            "Android rejected a BLE characteristic write",
        )
        val result = awaitOperation<AndroidGattOperation.CharacteristicWrite> { it.uuid == characteristic.uuid }
        requireGattSuccess(result.status, "characteristic write")
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        closeGatt()
        operations.close()
        incoming.close()
    }

    private fun closeGatt() {
        ownedGatt.getAndSet(null)?.let { resource ->
            runCatching { resource.disconnect() }
            runCatching { resource.close() }
        }
    }

    private suspend inline fun <reified T : AndroidGattOperation> awaitOperation(
        crossinline predicate: (T) -> Boolean = { true },
    ): T {
        while (true) {
            when (val event = operations.receive()) {
                is AndroidGattOperation.Disconnected -> throw androidTransportFailure(
                    "ble_disconnected",
                    "The Android BLE peer disconnected with status ${event.status}",
                )
                is T -> if (predicate(event)) return event
                else -> Unit
            }
        }
    }
}

private class AndroidCentralGattConnection(
    private val session: AndroidCentralGattSession,
    private val state: BluetoothGattCharacteristic,
    private val clientToServer: BluetoothGattCharacteristic,
) : BleRawConnection {
    override val bearer: BleRawBearer = BleRawBearer.GATT
    override val incoming = session.incoming
    override val maximumGattPacketBytes: Int
        get() = min(BLE_MAX_GATT_PACKET_BYTES, session.mtu - 3).coerceAtLeast(2)

    override suspend fun write(bytes: ByteArray) = session.write(clientToServer, bytes)
    override suspend fun finish() = session.write(state, byteArrayOf(BLE_STATE_END))
    override fun close(reason: ProximityCloseReason) = session.close()
}
