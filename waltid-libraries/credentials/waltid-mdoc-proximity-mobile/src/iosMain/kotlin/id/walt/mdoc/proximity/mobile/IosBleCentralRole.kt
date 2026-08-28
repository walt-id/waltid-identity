@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.proximity.ProximityCloseReason
import id.walt.mdoc.proximity.ProximityError
import id.walt.mdoc.proximity.ProximityException
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCentralManagerStatePoweredOn
import platform.CoreBluetooth.CBCentralManagerStateUnknown
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBCharacteristicWriteWithoutResponse
import platform.CoreBluetooth.CBL2CAPChannel
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralDelegateProtocol
import platform.CoreBluetooth.CBService
import platform.CoreBluetooth.CBUUID
import platform.CoreFoundation.CFAbsoluteTime
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.darwin.NSObject
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

internal class IosBleCentralRole(
    override val serviceUuid: BleServiceUuid,
    private val expectedIdent: ByteArray,
    private val preferL2cap: Boolean,
    private val sessionScope: CoroutineScope,
) : BlePreparedPlatformRole {
    override val role: BlePlatformRole = BlePlatformRole.CENTRAL_CLIENT
    override val l2capPsm: UInt? = null
    private val started = atomic(false)
    private val closed = atomic(false)
    private val session = IosCentralGattSession(serviceUuid)
    private var connection: BleRawConnection? = null
    private val completion: DisposableHandle? = sessionScope.coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion {
        close(ProximityCloseReason.CANCELLED)
    }

    override suspend fun awaitConnection(): BleRawConnection = withContext(Dispatchers.Main) {
        check(started.compareAndSet(false, true)) { "A prepared BLE central-client role can be awaited only once" }
        try {
            val characteristics = session.connectAndDiscover()
            val ident = session.read(characteristics.ident)
            if (!BleIdent.matches(expectedIdent, ident)) throw ProximityException(
                ProximityError.Security("ble_ident_mismatch", "The reader BLEIdent does not match EDeviceKeyBytes")
            )
            if (preferL2cap && characteristics.psm != null) {
                val psm = BlePsmCodec.decode(session.read(characteristics.psm)).toUShort()
                session.openL2cap(psm)?.let { channel ->
                    return@withContext IosL2capConnection(channel, sessionScope, session::close).also { connection = it }
                }
            }
            session.subscribe(characteristics.state)
            session.subscribe(characteristics.serverToClient)
            session.write(characteristics.state, byteArrayOf(BLE_STATE_START))
            IosCentralGattConnection(session, characteristics.state, characteristics.clientToServer).also {
                connection = it
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            close(ProximityCloseReason.CANCELLED)
            throw failure
        }
    }

    override fun close(reason: ProximityCloseReason) {
        if (!closed.compareAndSet(false, true)) return
        completion?.dispose()
        connection?.close(reason) ?: session.close()
    }
}

private data class IosReaderCharacteristics(
    val state: CBCharacteristic,
    val clientToServer: CBCharacteristic,
    val serverToClient: CBCharacteristic,
    val ident: CBCharacteristic,
    val psm: CBCharacteristic?,
)

private sealed interface IosCentralEvent {
    data class State(val value: Long) : IosCentralEvent
    data class Discovered(val peripheral: CBPeripheral) : IosCentralEvent
    data class Connected(val peripheral: CBPeripheral) : IosCentralEvent
    data class ConnectFailed(val error: NSError?) : IosCentralEvent
    data class Disconnected(val error: NSError?) : IosCentralEvent
    data class Services(val error: NSError?) : IosCentralEvent
    data class Characteristics(val service: CBService, val error: NSError?) : IosCentralEvent
    data class Value(val characteristic: CBCharacteristic, val error: NSError?) : IosCentralEvent
    data class NotificationState(val characteristic: CBCharacteristic, val error: NSError?) : IosCentralEvent
    data object ReadyToWrite : IosCentralEvent
    data class L2cap(val channel: CBL2CAPChannel?, val error: NSError?) : IosCentralEvent
}

private class IosCentralGattSession(private val serviceUuid: BleServiceUuid) {
    val incoming = Channel<ByteArray>(Channel.BUFFERED)
    private val events = Channel<IosCentralEvent>(Channel.UNLIMITED)
    private val closed = atomic(false)
    private var peripheral: CBPeripheral? = null
    private val central: CBCentralManager
    var maximumPacketBytes: Int = 20
        private set

    private val peripheralDelegate = object : NSObject(), CBPeripheralDelegateProtocol {
        override fun peripheral(peripheral: CBPeripheral, didDiscoverServices: NSError?) {
            events.trySend(IosCentralEvent.Services(didDiscoverServices))
        }

        override fun peripheral(
            peripheral: CBPeripheral,
            didDiscoverCharacteristicsForService: CBService,
            error: NSError?,
        ) {
            events.trySend(IosCentralEvent.Characteristics(didDiscoverCharacteristicsForService, error))
        }

        @ObjCSignatureOverride
        override fun peripheral(
            peripheral: CBPeripheral,
            didUpdateNotificationStateForCharacteristic: CBCharacteristic,
            error: NSError?,
        ) {
            events.trySend(IosCentralEvent.NotificationState(didUpdateNotificationStateForCharacteristic, error))
        }

        @ObjCSignatureOverride
        override fun peripheral(
            peripheral: CBPeripheral,
            didUpdateValueForCharacteristic: CBCharacteristic,
            error: NSError?,
        ) {
            val uuid = didUpdateValueForCharacteristic.UUID.UUIDString.lowercase()
            when (uuid) {
                BleGattUuid.READER_SERVER_TO_CLIENT -> {
                    if (error != null) incoming.close(error.asFailure("BLE notification failed"))
                    else incoming.trySend(didUpdateValueForCharacteristic.value?.toByteArray() ?: ByteArray(0))
                }
                BleGattUuid.READER_STATE -> {
                    when {
                        error != null -> incoming.close(error.asFailure("BLE state notification failed"))
                        didUpdateValueForCharacteristic.value?.toByteArray()
                            ?.contentEquals(byteArrayOf(BLE_STATE_END)) == true -> incoming.close()
                        else -> incoming.close(
                            ProximityException(
                                ProximityError.Protocol("invalid_ble_state", "The reader sent an invalid BLE state value")
                            )
                        )
                    }
                }
                else -> events.trySend(IosCentralEvent.Value(didUpdateValueForCharacteristic, error))
            }
        }

        override fun peripheralIsReadyToSendWriteWithoutResponse(peripheral: CBPeripheral) {
            events.trySend(IosCentralEvent.ReadyToWrite)
        }

        override fun peripheral(peripheral: CBPeripheral, didOpenL2CAPChannel: CBL2CAPChannel?, error: NSError?) {
            events.trySend(IosCentralEvent.L2cap(didOpenL2CAPChannel, error))
        }

        override fun peripheral(peripheral: CBPeripheral, didModifyServices: List<*>) {
            incoming.close(iosTransportFailure("ble_service_changed", "The reader BLE service changed during the session"))
        }
    }

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    private val centralDelegate = object : NSObject(), CBCentralManagerDelegateProtocol {
        override fun centralManagerDidUpdateState(cbCentralManager: CBCentralManager) {
            val state = cbCentralManager.state
            events.trySend(IosCentralEvent.State(state))
            if (state != CBCentralManagerStateUnknown && state != CBCentralManagerStatePoweredOn) {
                incoming.close(
                    iosTransportFailure("ble_unavailable", "CoreBluetooth became unavailable in state $state")
                )
            }
        }

        @ObjCSignatureOverride
        override fun centralManager(
            central: CBCentralManager,
            didDiscoverPeripheral: CBPeripheral,
            advertisementData: Map<Any?, *>,
            RSSI: NSNumber,
        ) {
            events.trySend(IosCentralEvent.Discovered(didDiscoverPeripheral))
        }

        @ObjCSignatureOverride
        override fun centralManager(central: CBCentralManager, didConnectPeripheral: CBPeripheral) {
            events.trySend(IosCentralEvent.Connected(didConnectPeripheral))
        }

        @ObjCSignatureOverride
        override fun centralManager(central: CBCentralManager, didFailToConnectPeripheral: CBPeripheral, error: NSError?) {
            events.trySend(IosCentralEvent.ConnectFailed(error))
        }

        @ObjCSignatureOverride
        override fun centralManager(
            central: CBCentralManager,
            didDisconnectPeripheral: CBPeripheral,
            error: NSError?,
        ) {
            handleDisconnect(error)
        }

        @ObjCSignatureOverride
        override fun centralManager(
            central: CBCentralManager,
            didDisconnectPeripheral: CBPeripheral,
            timestamp: CFAbsoluteTime,
            isReconnecting: Boolean,
            error: NSError?,
        ) {
            handleDisconnect(error)
        }

        private fun handleDisconnect(error: NSError?) {
            events.trySend(IosCentralEvent.Disconnected(error))
            incoming.close(error?.asFailure("The BLE reader disconnected"))
        }
    }

    init {
        central = CBCentralManager(delegate = centralDelegate, queue = null, options = null)
    }

    suspend fun connectAndDiscover(): IosReaderCharacteristics {
        awaitPoweredOn()
        central.scanForPeripheralsWithServices(
            listOf(CBUUID.UUIDWithString(serviceUuid.platformString())),
            options = null,
        )
        val discovered = awaitEvent<IosCentralEvent.Discovered>().peripheral
        central.stopScan()
        peripheral = discovered
        discovered.delegate = peripheralDelegate
        central.connectPeripheral(discovered, options = null)
        awaitEvent<IosCentralEvent.Connected>()
        discovered.discoverServices(listOf(CBUUID.UUIDWithString(serviceUuid.platformString())))
        awaitEvent<IosCentralEvent.Services>().error?.let { throw it.asFailure("BLE service discovery failed") }
        val service = discovered.services
            ?.filterIsInstance<CBService>()
            ?.singleOrNull { it.UUID.UUIDString.equals(serviceUuid.platformString(), ignoreCase = true) }
            ?: throw iosTransportFailure("ble_service_missing", "The reader does not expose the requested BLE service")
        discovered.discoverCharacteristics(characteristicUUIDs = null, forService = service)
        awaitEvent<IosCentralEvent.Characteristics> { it.service == service }.error?.let {
            throw it.asFailure("BLE characteristic discovery failed")
        }
        val available = service.characteristics?.filterIsInstance<CBCharacteristic>().orEmpty()
        fun required(uuid: String): CBCharacteristic = available.singleOrNull {
            it.UUID.UUIDString.equals(uuid, ignoreCase = true)
        } ?: throw iosTransportFailure("ble_characteristic_missing", "The reader BLE service is missing $uuid")
        maximumPacketBytes = min(
            BLE_MAX_GATT_PACKET_BYTES,
            discovered.maximumWriteValueLengthForType(CBCharacteristicWriteWithoutResponse).toInt(),
        ).coerceAtLeast(2)
        return IosReaderCharacteristics(
            state = required(BleGattUuid.READER_STATE),
            clientToServer = required(BleGattUuid.READER_CLIENT_TO_SERVER),
            serverToClient = required(BleGattUuid.READER_SERVER_TO_CLIENT),
            ident = required(BleGattUuid.READER_IDENT),
            psm = available.singleOrNull { it.UUID.UUIDString.equals(BleGattUuid.READER_L2CAP_PSM, ignoreCase = true) },
        )
    }

    suspend fun read(characteristic: CBCharacteristic): ByteArray {
        val peer = peripheral ?: throw iosTransportFailure("ble_disconnected", "The BLE reader is unavailable")
        peer.readValueForCharacteristic(characteristic)
        val event = awaitEvent<IosCentralEvent.Value> { it.characteristic == characteristic }
        event.error?.let { throw it.asFailure("BLE characteristic read failed") }
        return characteristic.value?.toByteArray() ?: ByteArray(0)
    }

    suspend fun subscribe(characteristic: CBCharacteristic) {
        val peer = peripheral ?: throw iosTransportFailure("ble_disconnected", "The BLE reader is unavailable")
        peer.setNotifyValue(true, forCharacteristic = characteristic)
        val event = awaitEvent<IosCentralEvent.NotificationState> { it.characteristic == characteristic }
        event.error?.let { throw it.asFailure("BLE notification subscription failed") }
        if (!characteristic.isNotifying) throw iosTransportFailure(
            "ble_subscribe_failed",
            "CoreBluetooth did not enable the required BLE notification",
        )
    }

    suspend fun write(characteristic: CBCharacteristic, bytes: ByteArray) {
        val peer = peripheral ?: throw iosTransportFailure("ble_disconnected", "The BLE reader is unavailable")
        while (!peer.canSendWriteWithoutResponse) awaitEvent<IosCentralEvent.ReadyToWrite>()
        peer.writeValue(bytes.toNSData(), forCharacteristic = characteristic, type = CBCharacteristicWriteWithoutResponse)
        // Keep close from racing the final State=End write while CoreBluetooth applies backpressure.
        if (!peer.canSendWriteWithoutResponse) awaitEvent<IosCentralEvent.ReadyToWrite>()
    }

    suspend fun openL2cap(psm: UShort): CBL2CAPChannel? {
        val peer = peripheral ?: return null
        peer.openL2CAPChannel(psm)
        val event = awaitEvent<IosCentralEvent.L2cap>()
        return if (event.error == null) event.channel else null
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        runOnIosBleQueue {
            central.stopScan()
            peripheral?.let { central.cancelPeripheralConnection(it) }
            peripheral?.delegate = null
            central.delegate = null
        }
        events.close()
        incoming.close()
    }

    private suspend fun awaitPoweredOn() {
        var state = central.state
        while (state == CBCentralManagerStateUnknown) state = awaitEvent<IosCentralEvent.State>().value
        if (state != CBCentralManagerStatePoweredOn) throw iosTransportFailure(
            "ble_powered_off",
            "CoreBluetooth is unavailable in state $state",
        )
    }

    private suspend inline fun <reified T : IosCentralEvent> awaitEvent(
        crossinline predicate: (T) -> Boolean = { true },
    ): T {
        while (true) {
            when (val event = events.receive()) {
                is IosCentralEvent.ConnectFailed -> throw event.error?.asFailure("BLE connection failed")
                    ?: iosTransportFailure("ble_connect_failed", "CoreBluetooth could not connect to the reader")
                is IosCentralEvent.Disconnected -> throw event.error?.asFailure("The BLE reader disconnected")
                    ?: iosTransportFailure("ble_disconnected", "The BLE reader disconnected")
                is T -> if (predicate(event)) return event
                is IosCentralEvent.State -> if (event.value != CBCentralManagerStatePoweredOn) {
                    throw iosTransportFailure(
                        "ble_powered_off",
                        "CoreBluetooth became unavailable in state ${event.value}",
                    )
                }
                else -> Unit
            }
        }
    }
}

private class IosCentralGattConnection(
    private val session: IosCentralGattSession,
    private val state: CBCharacteristic,
    private val clientToServer: CBCharacteristic,
) : BleRawConnection {
    override val bearer: BleRawBearer = BleRawBearer.GATT
    override val incoming = session.incoming
    override val maximumGattPacketBytes: Int get() = session.maximumPacketBytes

    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.Main) {
        session.write(clientToServer, bytes)
    }
    override suspend fun finish() = withContext(Dispatchers.Main) {
        session.write(state, byteArrayOf(BLE_STATE_END))
        // CoreBluetooth has no completion callback for a write without response when its queue
        // remains writable. Keep the link alive long enough for the final State=End command to
        // leave that queue before the common transport closes the connection.
        delay(IOS_GATT_TERMINATION_DRAIN_DELAY)
    }
    override fun close(reason: ProximityCloseReason) = session.close()
}

private val IOS_GATT_TERMINATION_DRAIN_DELAY = 1.seconds

private fun NSError.asFailure(message: String) = iosTransportFailure(
    "core_bluetooth_error",
    "$message: $localizedDescription",
)
