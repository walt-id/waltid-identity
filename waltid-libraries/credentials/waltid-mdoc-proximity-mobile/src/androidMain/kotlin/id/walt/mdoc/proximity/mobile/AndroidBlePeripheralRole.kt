package id.walt.mdoc.proximity.mobile

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import id.walt.mdoc.proximity.ProximityCloseReason
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@SuppressLint("MissingPermission")
internal class AndroidBlePeripheralRole private constructor(
    private val context: Context,
    private val manager: BluetoothManager,
    private val adapter: BluetoothAdapter,
    override val serviceUuid: BleServiceUuid,
    private val preferL2cap: Boolean,
    private val sessionScope: CoroutineScope,
) : BlePreparedPlatformRole {
    override val role: BlePlatformRole = BlePlatformRole.PERIPHERAL_SERVER
    override val l2capPsm: UInt? get() = l2capServer?.psm?.toUInt()
    private val closed = AtomicBoolean(false)
    private val startedAwait = AtomicBoolean(false)
    private val activeDevice = AtomicReference<BluetoothDevice?>(null)
    private val activeConnection = AtomicReference<BleRawConnection?>(null)
    private val mtu = AtomicInteger(23)
    private val stateNotificationsEnabled = AtomicBoolean(false)
    private val dataNotificationsEnabled = AtomicBoolean(false)
    private val connection = CompletableDeferred<BleRawConnection>()
    private val serviceAdded = CompletableDeferred<Unit>()
    private val notifications = Channel<Int>(Channel.UNLIMITED)
    private val incomingGatt = Channel<ByteArray>(Channel.BUFFERED)
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertisingCallback: AdvertiseCallback? = null
    private var l2capServer: BluetoothServerSocket? = null
    private var completion: DisposableHandle? = null

    private val stateCharacteristic = characteristic(
        BleGattUuid.MDOC_STATE,
        BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
        BluetoothGattCharacteristic.PERMISSION_WRITE,
        notify = true,
    )
    private val clientToServerCharacteristic = characteristic(
        BleGattUuid.MDOC_CLIENT_TO_SERVER,
        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
        BluetoothGattCharacteristic.PERMISSION_WRITE,
    )
    private val serverToClientCharacteristic = characteristic(
        BleGattUuid.MDOC_SERVER_TO_CLIENT,
        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        0,
        notify = true,
    )
    private val psmCharacteristic = characteristic(
        BleGattUuid.MDOC_L2CAP_PSM,
        BluetoothGattCharacteristic.PROPERTY_READ,
        BluetoothGattCharacteristic.PERMISSION_READ,
    )

    private val callback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            if (status == BluetoothGatt.GATT_SUCCESS) serviceAdded.complete(Unit)
            else serviceAdded.completeExceptionally(
                androidTransportFailure("ble_service_add_failed", "Android failed to add the mdoc GATT service: $status")
            )
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                val accepted = activeDevice.compareAndSet(null, device) || activeDevice.get()?.address == device.address
                if (!accepted) gattServer?.cancelConnection(device)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED && activeDevice.get()?.address == device.address) {
                activeDevice.set(null)
                stateNotificationsEnabled.set(false)
                dataNotificationsEnabled.set(false)
                if (activeConnection.get()?.bearer == BleRawBearer.GATT) {
                    incomingGatt.close(
                        if (status == BluetoothGatt.GATT_SUCCESS) null
                        else androidTransportFailure(
                            "android_gatt_failure",
                            "The Android BLE reader disconnected with status $status",
                        )
                    )
                }
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            if (activeDevice.get()?.address == device.address) this@AndroidBlePeripheralRole.mtu.set(mtu)
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val psm = l2capPsm
            val validPeer = activeDevice.get()?.address == device.address
            if (validPeer && characteristic.uuid == psmCharacteristic.uuid && psm != null && offset == 0) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, BlePsmCodec.encode(psm))
            } else {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            val validPeer = activeDevice.get()?.address == device.address
            val accepted = validPeer && !preparedWrite && offset == 0 && when (characteristic.uuid) {
                stateCharacteristic.uuid -> handleState(value)
                clientToServerCharacteristic.uuid -> handleIncomingGatt(value)
                else -> false
            }
            if (responseNeeded) gattServer?.sendResponse(
                device,
                requestId,
                if (accepted) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE,
                offset,
                null,
            )
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor,
        ) {
            val notificationState = when (descriptor.characteristic.uuid) {
                stateCharacteristic.uuid -> stateNotificationsEnabled
                serverToClientCharacteristic.uuid -> dataNotificationsEnabled
                else -> null
            }
            val accepted = activeDevice.get()?.address == device.address &&
                descriptor.uuid == UUID.fromString(BleGattUuid.CLIENT_CHARACTERISTIC_CONFIGURATION) &&
                notificationState != null && offset == 0
            gattServer?.sendResponse(
                device,
                requestId,
                if (accepted) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE,
                offset,
                if (notificationState?.get() == true) {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                } else {
                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                },
            )
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            val notificationState = when (descriptor.characteristic.uuid) {
                stateCharacteristic.uuid -> stateNotificationsEnabled
                serverToClientCharacteristic.uuid -> dataNotificationsEnabled
                else -> null
            }
            val enable = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            val disable = value.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
            val accepted = activeDevice.get()?.address == device.address && !preparedWrite && offset == 0 &&
                descriptor.uuid == UUID.fromString(BleGattUuid.CLIENT_CHARACTERISTIC_CONFIGURATION) &&
                notificationState != null && (enable || disable)
            if (accepted) notificationState.set(enable)
            if (responseNeeded) gattServer?.sendResponse(
                device,
                requestId,
                if (accepted) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE,
                offset,
                null,
            )
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            notifications.trySend(status)
        }
    }

    private suspend fun start() {
        if (preferL2cap) {
            l2capServer = runCatching { adapter.listenUsingInsecureL2capChannel() }.getOrNull()
            if (l2capServer?.psm?.toUInt()?.let(BlePsmCodec::isDynamicLe) == false) {
                closeL2capListener()
            }
            l2capServer?.let { server ->
                sessionScope.launch(Dispatchers.IO) { acceptL2cap(server) }
            }
        }
        gattServer = manager.openGattServer(context, callback)
            ?: throw androidTransportFailure("ble_gatt_server_failed", "Android could not open a GATT server")
        val service = BluetoothGattService(
            UUID.fromString(serviceUuid.platformString()),
            BluetoothGattService.SERVICE_TYPE_PRIMARY,
        ).apply {
            addCharacteristic(stateCharacteristic)
            addCharacteristic(clientToServerCharacteristic)
            addCharacteristic(serverToClientCharacteristic)
            if (l2capServer != null) addCharacteristic(psmCharacteristic)
        }
        if (gattServer?.addService(service) != true) throw androidTransportFailure(
            "ble_service_add_failed",
            "Android rejected the mdoc GATT service",
        )
        serviceAdded.await()
        startAdvertising()
        completion = sessionScope.coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion {
            close(ProximityCloseReason.CANCELLED)
        }
    }

    private suspend fun startAdvertising() = suspendCancellableCoroutine { continuation ->
        val leAdvertiser = adapter.bluetoothLeAdvertiser
            ?: return@suspendCancellableCoroutine continuation.resumeWithException(
                androidTransportFailure("ble_advertiser_unavailable", "The Android BLE advertiser is unavailable")
            )
        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                continuation.resume(Unit)
            }

            override fun onStartFailure(errorCode: Int) {
                advertisingCallback = null
                continuation.resumeWithException(
                    androidTransportFailure("ble_advertise_failed", "Android BLE advertising failed with error $errorCode")
                )
            }
        }
        advertiser = leAdvertiser
        advertisingCallback = callback
        continuation.invokeOnCancellation {
            runCatching { leAdvertiser.stopAdvertising(callback) }
            advertisingCallback = null
        }
        leAdvertiser.startAdvertising(
            AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build(),
            AdvertiseData.Builder().addServiceUuid(ParcelUuid(UUID.fromString(serviceUuid.platformString()))).build(),
            callback,
        )
    }

    private fun handleState(value: ByteArray): Boolean = when {
        value.contentEquals(byteArrayOf(BLE_STATE_START)) -> {
            if (!stateNotificationsEnabled.get() || !dataNotificationsEnabled.get()) return false
            val raw = AndroidPeripheralGattConnection(this, incomingGatt)
            if (activeConnection.compareAndSet(null, raw) && connection.complete(raw)) {
                stopAdvertising()
                closeL2capListener()
                true
            } else {
                activeConnection.compareAndSet(raw, null)
                false
            }
        }
        value.contentEquals(byteArrayOf(BLE_STATE_END)) -> {
            incomingGatt.close()
            true
        }
        else -> false
    }

    private fun handleIncomingGatt(value: ByteArray): Boolean {
        if (activeConnection.get()?.bearer != BleRawBearer.GATT) return false
        return incomingGatt.trySend(value.copyOf()).isSuccess
    }

    private suspend fun acceptL2cap(server: BluetoothServerSocket) {
        try {
            while (!closed.get() && !connection.isCompleted) {
                val socket = withContext(Dispatchers.IO) { server.accept() }
                val controlPeer = activeDevice.get()
                if (controlPeer != null && controlPeer.address != socket.remoteDevice.address) {
                    runCatching { socket.close() }
                    continue
                }
                closeL2capListener()
                val raw = AndroidL2capConnection(socket, sessionScope)
                if (activeConnection.compareAndSet(null, raw) && connection.complete(raw)) {
                    stopGattInfrastructure()
                } else {
                    activeConnection.compareAndSet(raw, null)
                    raw.close(ProximityCloseReason.LOST_RACE)
                }
                return
            }
        } catch (failure: Exception) {
            if (!closed.get() && !connection.isCompleted) {
                // L2CAP is optional; GATT remains active after an accept failure.
            }
        }
    }

    override suspend fun awaitConnection(): BleRawConnection {
        check(startedAwait.compareAndSet(false, true)) { "A prepared BLE peripheral-server role can be awaited only once" }
        return try {
            connection.await()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            close(ProximityCloseReason.CANCELLED)
            throw failure
        }
    }

    internal fun maximumGattPacketBytes(): Int = min(BLE_MAX_GATT_PACKET_BYTES, mtu.get() - 3).coerceAtLeast(2)

    internal suspend fun notify(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        val device = activeDevice.get() ?: throw androidTransportFailure(
            "ble_disconnected",
            "The Android BLE reader is no longer connected",
        )
        val server = gattServer ?: throw androidTransportFailure("ble_disconnected", "The Android GATT server is closed")
        val subscribed = when (characteristic.uuid) {
            stateCharacteristic.uuid -> stateNotificationsEnabled.get()
            serverToClientCharacteristic.uuid -> dataNotificationsEnabled.get()
            else -> false
        }
        if (!subscribed) throw androidTransportFailure(
            "ble_not_subscribed",
            "The Android BLE reader is not subscribed to the required notification characteristic",
        )
        if (!server.notifyCompat(device, characteristic, value)) throw androidTransportFailure(
            "ble_notify_failed",
            "Android rejected a BLE notification",
        )
        requireGattSuccess(notifications.receive(), "notification")
    }

    internal fun closeFromConnection(reason: ProximityCloseReason) {
        if (!closed.compareAndSet(false, true)) return
        completion?.dispose()
        stopAdvertising()
        closeL2capListener()
        activeDevice.getAndSet(null)?.let { device -> runCatching { gattServer?.cancelConnection(device) } }
        runCatching { gattServer?.clearServices() }
        runCatching { gattServer?.close() }
        gattServer = null
        incomingGatt.close()
        notifications.close()
    }

    override fun close(reason: ProximityCloseReason) {
        if (!closed.compareAndSet(false, true)) return
        completion?.dispose()
        activeConnection.getAndSet(null)?.close(reason)
        stopAdvertising()
        closeL2capListener()
        activeDevice.getAndSet(null)?.let { device -> runCatching { gattServer?.cancelConnection(device) } }
        runCatching { gattServer?.clearServices() }
        runCatching { gattServer?.close() }
        gattServer = null
        if (!connection.isCompleted) connection.cancel()
        incomingGatt.close()
        notifications.close()
    }

    private fun stopAdvertising() {
        advertisingCallback?.let { callback -> runCatching { advertiser?.stopAdvertising(callback) } }
        advertisingCallback = null
        advertiser = null
    }

    private fun closeL2capListener() {
        runCatching { l2capServer?.close() }
        l2capServer = null
    }

    private fun stopGattInfrastructure() {
        stopAdvertising()
        activeDevice.getAndSet(null)?.let { device -> runCatching { gattServer?.cancelConnection(device) } }
        runCatching { gattServer?.clearServices() }
        runCatching { gattServer?.close() }
        gattServer = null
        incomingGatt.close()
        notifications.close()
    }

    internal val outgoingDataCharacteristic: BluetoothGattCharacteristic get() = serverToClientCharacteristic
    internal val state: BluetoothGattCharacteristic get() = stateCharacteristic

    internal companion object {
        suspend fun create(
            context: Context,
            manager: BluetoothManager,
            adapter: BluetoothAdapter,
            serviceUuid: BleServiceUuid,
            preferL2cap: Boolean,
            sessionScope: CoroutineScope,
        ): AndroidBlePeripheralRole = AndroidBlePeripheralRole(
            context,
            manager,
            adapter,
            serviceUuid,
            preferL2cap,
            sessionScope,
        ).also { role ->
            try {
                role.start()
            } catch (failure: Throwable) {
                role.close(ProximityCloseReason.CANCELLED)
                throw failure
            }
        }
    }
}

private fun characteristic(
    uuid: String,
    properties: Int,
    permissions: Int,
    notify: Boolean = false,
): BluetoothGattCharacteristic = BluetoothGattCharacteristic(UUID.fromString(uuid), properties, permissions).apply {
    if (notify) addDescriptor(
        BluetoothGattDescriptor(
            UUID.fromString(BleGattUuid.CLIENT_CHARACTERISTIC_CONFIGURATION),
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
        )
    )
}

private class AndroidPeripheralGattConnection(
    private val owner: AndroidBlePeripheralRole,
    override val incoming: Channel<ByteArray>,
) : BleRawConnection {
    override val bearer: BleRawBearer = BleRawBearer.GATT
    override val maximumGattPacketBytes: Int get() = owner.maximumGattPacketBytes()
    private val closed = AtomicBoolean(false)

    override suspend fun write(bytes: ByteArray) = owner.notify(owner.outgoingDataCharacteristic, bytes)
    override suspend fun finish() = owner.notify(owner.state, byteArrayOf(BLE_STATE_END))

    override fun close(reason: ProximityCloseReason) {
        if (!closed.compareAndSet(false, true)) return
        owner.closeFromConnection(reason)
    }
}
