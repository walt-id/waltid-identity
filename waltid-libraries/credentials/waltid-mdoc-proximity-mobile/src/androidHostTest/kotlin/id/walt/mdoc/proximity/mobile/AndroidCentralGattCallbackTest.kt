@file:Suppress("DEPRECATION")

package id.walt.mdoc.proximity.mobile

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowBluetoothDevice
import org.robolectric.shadows.ShadowBluetoothGatt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [PendingGatt::class])
class AndroidCentralGattCallbackTest {
    @Test fun foreignGattCannotDeliverDataOrCompleteAnOperation() = runTest {
        val device = ShadowBluetoothDevice.newInstance("00:11:22:33:44:55")
        val session = AndroidCentralGattSession(RuntimeEnvironment.getApplication(), device)
        val connecting = async { runCatching { session.connect() } }
        runCurrent()
        val owned = shadowOf(device).bluetoothGatts.single()
        val callback = shadowOf(owned).gattCallback
        val foreign = ShadowBluetoothGatt.newInstance(device)
        val characteristic = characteristic(BleGattUuid.READER_SERVER_TO_CLIENT)
        val descriptor = BluetoothGattDescriptor(UUID.randomUUID(), 0)
        callback.onCharacteristicChanged(foreign, characteristic, byteArrayOf(1, 2))
        callback.onCharacteristicRead(foreign, characteristic, byteArrayOf(3), 0)
        callback.onCharacteristicWrite(foreign, characteristic, 0)
        callback.onDescriptorWrite(foreign, descriptor, 0)
        callback.onMtuChanged(foreign, 100, 0)
        callback.onServicesDiscovered(foreign, 0)
        callback.onConnectionStateChange(foreign, 0, BluetoothProfile.STATE_DISCONNECTED)
        assertTrue(session.incoming.tryReceive().isFailure, "foreign payload must not enter the holder")
        assertTrue(session.operations.tryReceive().isFailure, "foreign callbacks must not advance an operation")
        assertTrue(connecting.isActive)
        // The matching callback still establishes this same session.
        callback.onConnectionStateChange(owned, 0, BluetoothProfile.STATE_CONNECTED)
        runCurrent()
        assertTrue(connecting.await().isSuccess)
        session.close()
        assertTrue(shadowOf(owned).isClosed)
    }

    @Test fun notificationSnapshotsBytesAndIgnoresUnknownCharacteristics() = runTest {
        val device = ShadowBluetoothDevice.newInstance("00:11:22:33:44:55")
        val session = AndroidCentralGattSession(RuntimeEnvironment.getApplication(), device)
        val connecting = async { runCatching { session.connect() } }
        runCurrent()
        val gatt = shadowOf(device).bluetoothGatts.single()
        val callback = shadowOf(gatt).gattCallback
        callback.onCharacteristicChanged(gatt, characteristic(UUID.randomUUID().toString()), byteArrayOf(9))
        assertTrue(session.incoming.tryReceive().isFailure)
        val bytes = byteArrayOf(0, 7, 8)
        callback.onCharacteristicChanged(gatt, characteristic(BleGattUuid.READER_SERVER_TO_CLIENT), bytes)
        bytes[1] = 99
        assertContentEquals(byteArrayOf(0, 7, 8), session.incoming.receive())
        session.close()
        connecting.cancelAndJoin()
        callback.onCharacteristicChanged(gatt, characteristic(BleGattUuid.READER_SERVER_TO_CLIENT), byteArrayOf(4))
        assertTrue(session.incoming.receiveCatching().isClosed)
        assertTrue(shadowOf(gatt).isClosed)
    }

    @Test fun disconnectIsTerminalAndLateCallbacksCannotReviveSession() = runTest {
        val device = ShadowBluetoothDevice.newInstance("00:11:22:33:44:55")
        val session = AndroidCentralGattSession(RuntimeEnvironment.getApplication(), device)
        val connecting = async { runCatching { session.connect() } }
        runCurrent()
        val gatt = shadowOf(device).bluetoothGatts.single()
        val callback = shadowOf(gatt).gattCallback
        callback.onConnectionStateChange(gatt, 0, BluetoothProfile.STATE_DISCONNECTED)
        runCurrent()
        assertTrue(connecting.await().isFailure)
        callback.onConnectionStateChange(gatt, 0, BluetoothProfile.STATE_CONNECTED)
        callback.onMtuChanged(gatt, 500, 0)
        assertTrue(session.operations.tryReceive().isFailure, "disconnect must discard late callbacks")
        assertTrue(session.incoming.receiveCatching().isClosed)
        session.close()
        session.close()
        assertTrue(shadowOf(gatt).isClosed)
        // Native session ownership is per instance: recovery creates a fresh GATT.
        val fresh = AndroidCentralGattSession(RuntimeEnvironment.getApplication(), device)
        val recovered = async { runCatching { fresh.connect() } }
        runCurrent()
        val next = shadowOf(device).bluetoothGatts.last()
        shadowOf(next).gattCallback.onConnectionStateChange(next, 0, BluetoothProfile.STATE_CONNECTED)
        runCurrent()
        assertTrue(recovered.await().isSuccess)
        fresh.close()
    }

    @Test fun closeUnblocksPendingConnectionWithoutAdvancingVirtualTime() = runTest {
        val device = ShadowBluetoothDevice.newInstance("00:11:22:33:44:55")
        val session = AndroidCentralGattSession(RuntimeEnvironment.getApplication(), device)
        val connecting = async { runCatching { session.connect() } }
        runCurrent()
        session.close()
        runCurrent()
        assertTrue(connecting.await().isFailure)
        assertEquals(0, testScheduler.currentTime)
        assertTrue(shadowOf(shadowOf(device).bluetoothGatts.single()).isClosed)
    }

    @Test fun writeWaitsForMatchingCompletionAndDiscardsAlreadyDeliveredDuplicates() = runTest {
        val device = ShadowBluetoothDevice.newInstance("00:11:22:33:44:55")
        val session = AndroidCentralGattSession(RuntimeEnvironment.getApplication(), device)
        val connecting = async { runCatching { session.connect() } }
        runCurrent()
        val gatt = shadowOf(device).bluetoothGatts.single()
        val callback = shadowOf(gatt).gattCallback
        callback.onConnectionStateChange(gatt, 0, BluetoothProfile.STATE_CONNECTED)
        runCurrent()
        assertTrue(connecting.await().isSuccess)
        val selected = characteristic(BleGattUuid.READER_CLIENT_TO_SERVER)
        val first = async { session.write(selected, byteArrayOf(1, 2, 3)) }
        runCurrent()
        val native = Shadow.extract<PendingGatt>(gatt)
        assertContentEquals(byteArrayOf(1, 2, 3), native.writes.single())
        callback.onCharacteristicWrite(gatt, characteristic(BleGattUuid.READER_STATE), 0)
        runCurrent()
        assertTrue(first.isActive, "wrong characteristic cannot complete the write")
        callback.onCharacteristicWrite(gatt, selected, 0)
        runCurrent()
        first.await()
        callback.onCharacteristicWrite(gatt, selected, 0) // Duplicate of the completed operation.
        val second = async { runCatching { session.write(selected, byteArrayOf(4, 5)) } }
        runCurrent()
        assertEquals(2, native.writes.size)
        assertTrue(second.isActive, "a queued duplicate cannot complete a new write")
        callback.onConnectionStateChange(gatt, 0, BluetoothProfile.STATE_DISCONNECTED)
        runCurrent()
        assertTrue(second.await().isFailure)
        assertTrue(native.isClosed)
    }

    @Test fun cccdCompletionMustBelongToTheSubscribedCharacteristic() = runTest {
        val device = ShadowBluetoothDevice.newInstance("00:11:22:33:44:55")
        val session = AndroidCentralGattSession(RuntimeEnvironment.getApplication(), device)
        val connecting = async { runCatching { session.connect() } }
        runCurrent()
        val gatt = shadowOf(device).bluetoothGatts.single()
        val callback = shadowOf(gatt).gattCallback
        callback.onConnectionStateChange(gatt, 0, BluetoothProfile.STATE_CONNECTED)
        runCurrent()
        assertTrue(connecting.await().isSuccess)
        fun notifying(uuid: String) = characteristic(uuid).apply {
            addDescriptor(BluetoothGattDescriptor(UUID.fromString(BleGattUuid.CLIENT_CHARACTERISTIC_CONFIGURATION), 0))
            shadowOf(gatt).allowCharacteristicNotification(this)
        }
        val selected = notifying(BleGattUuid.READER_SERVER_TO_CLIENT)
        val other = notifying(BleGattUuid.READER_STATE)
        val subscribing = async { session.subscribe(selected) }
        runCurrent()
        callback.onDescriptorWrite(gatt, other.descriptors.single(), 0)
        runCurrent()
        assertTrue(subscribing.isActive, "CCCD UUID alone is shared by both characteristics")
        callback.onDescriptorWrite(gatt, selected.descriptors.single(), 0)
        runCurrent()
        subscribing.await()
        session.close()
    }

    private fun characteristic(uuid: String) = BluetoothGattCharacteristic(UUID.fromString(uuid), 0, 0)
}

/** Records the native requests and leaves callback scheduling entirely under each test's control. */
@Implements(BluetoothGatt::class)
class PendingGatt : ShadowBluetoothGatt() {
    val writes = mutableListOf<ByteArray>()
    @Implementation
    public override fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, value: ByteArray, writeType: Int): Int {
        writes += value.copyOf()
        return 0
    }
    @Implementation
    public override fun writeDescriptor(descriptor: BluetoothGattDescriptor, value: ByteArray): Int = 0
}
