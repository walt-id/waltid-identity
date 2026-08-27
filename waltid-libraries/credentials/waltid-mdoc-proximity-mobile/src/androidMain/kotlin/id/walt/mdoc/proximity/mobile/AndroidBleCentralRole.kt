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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    @Volatile private var scanCallback: ScanCallback? = null
    @Volatile private var gattSession: AndroidCentralGattSession? = null
    @Volatile private var connection: BleRawConnection? = null
    private val completion: DisposableHandle? = sessionScope.coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion {
        close(ProximityCloseReason.CANCELLED)
    }

    override suspend fun awaitConnection(): BleRawConnection {
        check(started.compareAndSet(false, true)) { "A prepared BLE central-client role can be awaited only once" }
        return try {
            val device = scanForReader()
            val session = AndroidCentralGattSession(context, device).also { gattSession = it }
            session.connect()
            val established = establishBearer(session)
            connection = established
            established
        } catch (cancelled: CancellationException) {
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
    }

    private suspend fun establishBearer(session: AndroidCentralGattSession): BleRawConnection {
        val service = session.discover(UUID.fromString(serviceUuid.platformString()))
        val state = service.required(BleGattUuid.READER_STATE)
        val clientToServer = service.required(BleGattUuid.READER_CLIENT_TO_SERVER)
        val serverToClient = service.required(BleGattUuid.READER_SERVER_TO_CLIENT)
        val ident = service.required(BleGattUuid.READER_IDENT)
        val readerIdent = session.read(ident)
        if (!BleIdent.matches(expectedIdent, readerIdent)) throw ProximityException(
            ProximityError.Security("ble_ident_mismatch", "The reader BLEIdent does not match EDeviceKeyBytes")
        )

        if (preferL2cap) {
            val psmCharacteristic = service.getCharacteristic(UUID.fromString(BleGattUuid.READER_L2CAP_PSM))
            val psm = psmCharacteristic?.let { characteristic ->
                BlePsmCodec.decode(session.read(characteristic)).toInt()
            }
            if (psm != null) {
                openL2cap(session.device, psm)?.let { socket ->
                    session.close()
                    gattSession = null
                    return AndroidL2capConnection(socket, sessionScope)
                }
            }
        }

        session.subscribe(state)
        session.subscribe(serverToClient)
        session.write(state, byteArrayOf(BLE_STATE_START))
        return AndroidCentralGattConnection(session, state, clientToServer)
    }

    private suspend fun openL2cap(device: BluetoothDevice, psm: Int): android.bluetooth.BluetoothSocket? {
        val socket = runCatching { device.createInsecureL2capChannel(psm) }.getOrNull() ?: return null
        return try {
            val connected = withTimeoutOrNull(5.seconds) {
                withContext(Dispatchers.IO) { socket.connect() }
                true
            } == true
            if (connected) socket else null.also { runCatching { socket.close() } }
        } catch (cancelled: CancellationException) {
            runCatching { socket.close() }
            throw cancelled
        } catch (_: Exception) {
            runCatching { socket.close() }
            null
        }
    }

    override fun close(reason: ProximityCloseReason) {
        if (!closed.compareAndSet(false, true)) return
        completion?.dispose()
        scanCallback?.let { callback -> runCatching { adapter.bluetoothLeScanner?.stopScan(callback) } }
        scanCallback = null
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
            UUID.fromString(BleGattUuid.READER_SERVER_TO_CLIENT) -> incoming.trySend(value)
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
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            ?: throw androidTransportFailure("ble_connect_failed", "Android could not create a GATT connection")
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
        runCatching { gatt.disconnect() }
        runCatching { gatt.close() }
        operations.close()
        incoming.close()
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
