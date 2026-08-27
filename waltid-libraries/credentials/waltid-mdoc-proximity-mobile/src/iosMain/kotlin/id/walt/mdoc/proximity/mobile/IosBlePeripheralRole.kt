@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.proximity.ProximityCloseReason
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import platform.CoreBluetooth.CBATTErrorInvalidOffset
import platform.CoreBluetooth.CBATTErrorRequestNotSupported
import platform.CoreBluetooth.CBATTErrorSuccess
import platform.CoreBluetooth.CBATTRequest
import platform.CoreBluetooth.CBAdvertisementDataServiceUUIDsKey
import platform.CoreBluetooth.CBAttributePermissionsReadable
import platform.CoreBluetooth.CBAttributePermissionsWriteable
import platform.CoreBluetooth.CBCentral
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBCharacteristicPropertyNotify
import platform.CoreBluetooth.CBCharacteristicPropertyRead
import platform.CoreBluetooth.CBCharacteristicPropertyWriteWithoutResponse
import platform.CoreBluetooth.CBL2CAPChannel
import platform.CoreBluetooth.CBL2CAPPSM
import platform.CoreBluetooth.CBMutableCharacteristic
import platform.CoreBluetooth.CBMutableService
import platform.CoreBluetooth.CBPeripheralManager
import platform.CoreBluetooth.CBPeripheralManagerDelegateProtocol
import platform.CoreBluetooth.CBPeripheralManagerStatePoweredOn
import platform.CoreBluetooth.CBPeripheralManagerStateUnknown
import platform.CoreBluetooth.CBService
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSArray
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlin.math.min

internal class IosBlePeripheralRole private constructor(
    override val serviceUuid: BleServiceUuid,
    private val preferL2cap: Boolean,
    private val sessionScope: CoroutineScope,
) : BlePreparedPlatformRole {
    override val role: BlePlatformRole = BlePlatformRole.PERIPHERAL_SERVER
    override val l2capPsm: UInt? get() = publishedPsm.value?.toUInt()
    private val closed = atomic(false)
    private val startedAwait = atomic(false)
    private val publishedPsm = atomic<Int?>(null)
    private val activeCentral = atomic<CBCentral?>(null)
    private val activeConnection = atomic<BleRawConnection?>(null)
    private val stateNotificationsEnabled = atomic(false)
    private val dataNotificationsEnabled = atomic(false)
    private val maximumPacketBytes = atomic(20)
    private val connection = CompletableDeferred<BleRawConnection>()
    private val events = Channel<IosPeripheralEvent>(Channel.UNLIMITED)
    private val readyToUpdate = Channel<Unit>(Channel.CONFLATED)
    private val incomingGatt = Channel<ByteArray>(Channel.BUFFERED)
    private val manager: CBPeripheralManager
    private var service: CBMutableService? = null
    private var completion: DisposableHandle? = null

    private lateinit var stateCharacteristic: CBMutableCharacteristic
    private lateinit var clientToServerCharacteristic: CBMutableCharacteristic
    private lateinit var serverToClientCharacteristic: CBMutableCharacteristic
    private var psmCharacteristic: CBMutableCharacteristic? = null

    private val delegate = object : NSObject(), CBPeripheralManagerDelegateProtocol {
        override fun peripheralManagerDidUpdateState(peripheral: CBPeripheralManager) {
            val state = peripheral.state
            events.trySend(IosPeripheralEvent.State(state))
            if (state != CBPeripheralManagerStateUnknown && state != CBPeripheralManagerStatePoweredOn) {
                val failure = iosTransportFailure(
                    "ble_unavailable",
                    "CoreBluetooth became unavailable in state $state",
                )
                if (!connection.isCompleted) connection.completeExceptionally(failure)
                incomingGatt.close(failure)
                activeConnection.value?.close(ProximityCloseReason.PEER_DISCONNECTED)
            }
        }

        override fun peripheralManager(peripheral: CBPeripheralManager, didAddService: CBService, error: NSError?) {
            events.trySend(IosPeripheralEvent.ServiceAdded(didAddService, error))
        }

        override fun peripheralManagerDidStartAdvertising(peripheral: CBPeripheralManager, error: NSError?) {
            events.trySend(IosPeripheralEvent.AdvertisingStarted(error))
        }

        override fun peripheralManager(peripheral: CBPeripheralManager, didReceiveWriteRequests: List<*>) {
            didReceiveWriteRequests.filterIsInstance<CBATTRequest>().forEach(::handleWrite)
        }

        override fun peripheralManager(peripheral: CBPeripheralManager, didReceiveReadRequest: CBATTRequest) {
            val characteristic = didReceiveReadRequest.characteristic
            val value = l2capPsm?.let(BlePsmCodec::encode)
            val controlPeer = activeCentral.value
            val acceptedPeer = controlPeer?.identifier == didReceiveReadRequest.central.identifier ||
                activeCentral.compareAndSet(null, didReceiveReadRequest.central)
            when {
                acceptedPeer && characteristic == psmCharacteristic && value != null &&
                    didReceiveReadRequest.offset.toInt() == 0 -> {
                    didReceiveReadRequest.value = value.toNSData()
                    manager.respondToRequest(didReceiveReadRequest, CBATTErrorSuccess)
                }
                didReceiveReadRequest.offset.toInt() != 0 -> manager.respondToRequest(
                    didReceiveReadRequest,
                    CBATTErrorInvalidOffset,
                )
                else -> manager.respondToRequest(didReceiveReadRequest, CBATTErrorRequestNotSupported)
            }
        }

        override fun peripheralManagerIsReadyToUpdateSubscribers(peripheral: CBPeripheralManager) {
            readyToUpdate.trySend(Unit)
        }

        @ObjCSignatureOverride
        override fun peripheralManager(
            peripheral: CBPeripheralManager,
            central: CBCentral,
            didSubscribeToCharacteristic: CBCharacteristic,
        ) {
            val accepted = activeCentral.compareAndSet(null, central) ||
                activeCentral.value?.identifier == central.identifier
            if (!accepted) return
            when (didSubscribeToCharacteristic) {
                stateCharacteristic -> stateNotificationsEnabled.value = true
                serverToClientCharacteristic -> dataNotificationsEnabled.value = true
                else -> return
            }
            maximumPacketBytes.value = min(
                BLE_MAX_GATT_PACKET_BYTES,
                central.maximumUpdateValueLength.toInt(),
            ).coerceAtLeast(2)
        }

        @ObjCSignatureOverride
        override fun peripheralManager(
            peripheral: CBPeripheralManager,
            central: CBCentral,
            didUnsubscribeFromCharacteristic: CBCharacteristic,
        ) {
            val active = activeCentral.value
            if (active?.identifier != central.identifier) return
            when (didUnsubscribeFromCharacteristic) {
                stateCharacteristic -> stateNotificationsEnabled.value = false
                serverToClientCharacteristic -> dataNotificationsEnabled.value = false
                else -> return
            }
            if (activeConnection.value?.bearer == BleRawBearer.GATT) incomingGatt.close()
            else if (!stateNotificationsEnabled.value && !dataNotificationsEnabled.value) {
                activeCentral.compareAndSet(active, null)
            }
        }

        override fun peripheralManager(
            peripheral: CBPeripheralManager,
            didPublishL2CAPChannel: CBL2CAPPSM,
            error: NSError?,
        ) {
            events.trySend(IosPeripheralEvent.PublishedL2cap(didPublishL2CAPChannel, error))
        }

        override fun peripheralManager(
            peripheral: CBPeripheralManager,
            didOpenL2CAPChannel: CBL2CAPChannel?,
            error: NSError?,
        ) {
            if (error != null || didOpenL2CAPChannel == null) return
            val controlPeer = activeCentral.value
            val l2capPeer = didOpenL2CAPChannel.peer
            if (controlPeer != null && (l2capPeer == null || controlPeer.identifier != l2capPeer.identifier)) {
                didOpenL2CAPChannel.inputStream?.close()
                didOpenL2CAPChannel.outputStream?.close()
                return
            }
            val raw = IosL2capConnection(didOpenL2CAPChannel, sessionScope) {
                closeFromConnection(ProximityCloseReason.PEER_DISCONNECTED)
            }
            if (activeConnection.compareAndSet(null, raw) && connection.complete(raw)) {
                manager.stopAdvertising()
                unpublishL2cap()
            } else {
                activeConnection.compareAndSet(raw, null)
                raw.close(ProximityCloseReason.LOST_RACE)
            }
        }
    }

    init {
        manager = CBPeripheralManager(delegate = delegate, queue = null, options = null)
    }

    private suspend fun start() {
        awaitPoweredOn()
        if (preferL2cap) {
            manager.publishL2CAPChannelWithEncryption(false)
            val published = awaitEvent<IosPeripheralEvent.PublishedL2cap>()
            if (published.error == null) {
                if (BlePsmCodec.isDynamicLe(published.psm.toUInt())) publishedPsm.value = published.psm.toInt()
                else manager.unpublishL2CAPChannel(published.psm)
            }
        }
        stateCharacteristic = CBMutableCharacteristic(
            type = CBUUID.UUIDWithString(BleGattUuid.MDOC_STATE),
            properties = CBCharacteristicPropertyNotify + CBCharacteristicPropertyWriteWithoutResponse,
            value = null,
            permissions = CBAttributePermissionsWriteable,
        )
        clientToServerCharacteristic = CBMutableCharacteristic(
            type = CBUUID.UUIDWithString(BleGattUuid.MDOC_CLIENT_TO_SERVER),
            properties = CBCharacteristicPropertyWriteWithoutResponse,
            value = null,
            permissions = CBAttributePermissionsWriteable,
        )
        serverToClientCharacteristic = CBMutableCharacteristic(
            type = CBUUID.UUIDWithString(BleGattUuid.MDOC_SERVER_TO_CLIENT),
            properties = CBCharacteristicPropertyNotify,
            value = null,
            permissions = 0uL,
        )
        psmCharacteristic = l2capPsm?.let { psm ->
            CBMutableCharacteristic(
                type = CBUUID.UUIDWithString(BleGattUuid.MDOC_L2CAP_PSM),
                properties = CBCharacteristicPropertyRead,
                value = BlePsmCodec.encode(psm).toNSData(),
                permissions = CBAttributePermissionsReadable,
            )
        }
        service = CBMutableService(CBUUID.UUIDWithString(serviceUuid.platformString()), primary = true).also { service ->
            service.setCharacteristics(
                listOf(stateCharacteristic, clientToServerCharacteristic, serverToClientCharacteristic) +
                    listOfNotNull(psmCharacteristic)
            )
            manager.addService(service)
        }
        awaitEvent<IosPeripheralEvent.ServiceAdded> { it.service == service }.error?.let {
            throw it.asPeripheralFailure("CoreBluetooth could not add the mdoc GATT service")
        }
        manager.startAdvertising(
            mapOf(
                CBAdvertisementDataServiceUUIDsKey to
                    (listOf(CBUUID.UUIDWithString(serviceUuid.platformString())) as NSArray)
            )
        )
        awaitEvent<IosPeripheralEvent.AdvertisingStarted>().error?.let {
            throw it.asPeripheralFailure("CoreBluetooth could not advertise the mdoc GATT service")
        }
        completion = sessionScope.coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion {
            close(ProximityCloseReason.CANCELLED)
        }
    }

    private fun handleWrite(request: CBATTRequest) {
        val bytes = request.value?.toByteArray() ?: ByteArray(0)
        val central = activeCentral.value
        val sameCentral = central == null || central.identifier == request.central.identifier
        var terminate = false
        val accepted = sameCentral && request.offset.toInt() == 0 && when (request.characteristic) {
            stateCharacteristic -> handleState(request.central, bytes).let { result ->
                terminate = result == BlePeripheralStateCommandResult.TERMINATE
                result.accepted
            }
            clientToServerCharacteristic -> activeConnection.value?.bearer == BleRawBearer.GATT &&
                incomingGatt.trySend(bytes).isSuccess
            else -> false
        }
        manager.respondToRequest(request, if (accepted) CBATTErrorSuccess else CBATTErrorRequestNotSupported)
        if (accepted && terminate) close(ProximityCloseReason.PEER_DISCONNECTED)
    }

    private fun handleState(central: CBCentral, bytes: ByteArray): BlePeripheralStateCommandResult =
        evaluateBlePeripheralStateCommand(
            value = bytes,
            notificationsReady = stateNotificationsEnabled.value && dataNotificationsEnabled.value,
        ) {
            if (!activeCentral.compareAndSet(null, central) && activeCentral.value?.identifier != central.identifier) {
                false
            } else {
                maximumPacketBytes.value = min(
                    BLE_MAX_GATT_PACKET_BYTES,
                    central.maximumUpdateValueLength.toInt(),
                ).coerceAtLeast(2)
                val raw = IosPeripheralGattConnection(this, incomingGatt)
                if (activeConnection.compareAndSet(null, raw) && connection.complete(raw)) {
                    manager.stopAdvertising()
                    unpublishL2cap()
                    true
                } else {
                    activeConnection.compareAndSet(raw, null)
                    false
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

    internal fun maximumPacketBytes(): Int = maximumPacketBytes.value

    internal suspend fun notify(characteristic: CBMutableCharacteristic, bytes: ByteArray) {
        withContext(Dispatchers.Main) {
            val central = activeCentral.value ?: throw iosTransportFailure(
                "ble_disconnected",
                "The CoreBluetooth reader is no longer connected",
            )
            val subscribed = when (characteristic) {
                stateCharacteristic -> stateNotificationsEnabled.value
                serverToClientCharacteristic -> dataNotificationsEnabled.value
                else -> false
            }
            if (!subscribed) throw iosTransportFailure(
                "ble_not_subscribed",
                "The CoreBluetooth reader is not subscribed to the required notification characteristic",
            )
            while (!manager.updateValue(bytes.toNSData(), characteristic, listOf(central))) {
                readyToUpdate.receive()
            }
        }
    }

    internal fun closeFromConnection(reason: ProximityCloseReason) {
        if (!closed.compareAndSet(false, true)) return
        completion?.dispose()
        runOnIosBleQueue {
            manager.stopAdvertising()
            unpublishL2cap()
            manager.removeAllServices()
            manager.delegate = null
        }
        incomingGatt.close()
        stateNotificationsEnabled.value = false
        dataNotificationsEnabled.value = false
        readyToUpdate.close()
        events.close()
    }

    override fun close(reason: ProximityCloseReason) {
        if (!closed.compareAndSet(false, true)) return
        completion?.dispose()
        activeConnection.getAndSet(null)?.close(reason)
        runOnIosBleQueue {
            manager.stopAdvertising()
            unpublishL2cap()
            manager.removeAllServices()
            manager.delegate = null
        }
        if (!connection.isCompleted) connection.cancel()
        incomingGatt.close()
        stateNotificationsEnabled.value = false
        dataNotificationsEnabled.value = false
        readyToUpdate.close()
        events.close()
    }

    private fun unpublishL2cap() {
        publishedPsm.getAndSet(null)?.let { manager.unpublishL2CAPChannel(it.toUShort()) }
    }

    private suspend fun awaitPoweredOn() {
        var state = manager.state
        while (state == CBPeripheralManagerStateUnknown) state = awaitEvent<IosPeripheralEvent.State>().value
        if (state != CBPeripheralManagerStatePoweredOn) throw iosTransportFailure(
            "ble_powered_off",
            "CoreBluetooth is unavailable in state $state",
        )
    }

    private suspend inline fun <reified T : IosPeripheralEvent> awaitEvent(
        crossinline predicate: (T) -> Boolean = { true },
    ): T {
        while (true) {
            val event = events.receive()
            if (event is T && predicate(event)) return event
            if (event is IosPeripheralEvent.State && event.value != CBPeripheralManagerStatePoweredOn) {
                throw iosTransportFailure(
                    "ble_powered_off",
                    "CoreBluetooth became unavailable in state ${event.value}",
                )
            }
        }
    }

    internal val outgoingData: CBMutableCharacteristic get() = serverToClientCharacteristic
    internal val state: CBMutableCharacteristic get() = stateCharacteristic

    internal companion object {
        suspend fun create(
            serviceUuid: BleServiceUuid,
            preferL2cap: Boolean,
            sessionScope: CoroutineScope,
        ): IosBlePeripheralRole = IosBlePeripheralRole(serviceUuid, preferL2cap, sessionScope).also { role ->
            try {
                role.start()
            } catch (failure: Throwable) {
                role.close(ProximityCloseReason.CANCELLED)
                throw failure
            }
        }
    }
}

private sealed interface IosPeripheralEvent {
    data class State(val value: Long) : IosPeripheralEvent
    data class ServiceAdded(val service: CBService, val error: NSError?) : IosPeripheralEvent
    data class AdvertisingStarted(val error: NSError?) : IosPeripheralEvent
    data class PublishedL2cap(val psm: CBL2CAPPSM, val error: NSError?) : IosPeripheralEvent
}

private class IosPeripheralGattConnection(
    private val owner: IosBlePeripheralRole,
    override val incoming: Channel<ByteArray>,
) : BleRawConnection {
    override val bearer: BleRawBearer = BleRawBearer.GATT
    override val maximumGattPacketBytes: Int get() = owner.maximumPacketBytes()
    private val closed = atomic(false)

    override suspend fun write(bytes: ByteArray) = owner.notify(owner.outgoingData, bytes)
    override suspend fun finish() = owner.notify(owner.state, byteArrayOf(BLE_STATE_END))

    override fun close(reason: ProximityCloseReason) {
        if (!closed.compareAndSet(false, true)) return
        owner.closeFromConnection(reason)
    }
}

private fun NSError.asPeripheralFailure(message: String) = iosTransportFailure(
    "core_bluetooth_error",
    "$message: $localizedDescription",
)
