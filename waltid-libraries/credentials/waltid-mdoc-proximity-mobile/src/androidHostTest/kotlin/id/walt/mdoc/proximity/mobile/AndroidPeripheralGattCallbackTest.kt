package id.walt.mdoc.proximity.mobile

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowBluetoothDevice
import org.robolectric.shadows.ShadowBluetoothGattServer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import id.walt.mdoc.proximity.ProximityCloseReason

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [PendingGattServer::class])
class AndroidPeripheralGattCallbackTest {
    @Test fun wrongPeerAndQueuedDuplicateCannotAcknowledgeANotification() = runTest {
        val fixture = Fixture(backgroundScope)
        val raw = fixture.connect()
        val first = async { raw.write(byteArrayOf(0, 1, 2)) }
        runCurrent()
        assertContentEquals(byteArrayOf(0, 1, 2), fixture.native.writes.single())
        fixture.callback.onNotificationSent(fixture.other, 0)
        runCurrent()
        assertTrue(first.isActive, "a foreign central cannot acknowledge delivery")
        fixture.callback.onNotificationSent(fixture.peer, 0)
        runCurrent()
        first.await()
        fixture.callback.onNotificationSent(fixture.peer, 0)
        val next = async { raw.write(byteArrayOf(0, 3)) }
        runCurrent()
        assertTrue(next.isActive, "already delivered completion belongs to the previous notification")
        fixture.callback.onNotificationSent(fixture.peer, 0)
        runCurrent()
        next.await()
        assertEquals(2, fixture.native.writes.size)
        raw.close(ProximityCloseReason.COMPLETED)
        assertTrue(fixture.native.isClosed)
    }

    @Test fun disconnectWhileSendingUnblocksNotificationAndFreshRoleRecovers() = runTest {
        val fixture = Fixture(backgroundScope)
        val raw = fixture.connect()
        val pending = async { runCatching { raw.write(byteArrayOf(0, 1)) } }
        runCurrent()
        fixture.callback.onConnectionStateChange(fixture.peer, 0, BluetoothProfile.STATE_DISCONNECTED)
        runCurrent()
        assertTrue(pending.isCompleted, "disconnect must unblock a pending notification without its outer deadline")
        assertTrue(pending.await().isFailure)
        assertTrue(raw.incoming.receiveCatching().isClosed)
        raw.close(ProximityCloseReason.PEER_DISCONNECTED)
        assertTrue(fixture.native.isClosed)
        val fresh = Fixture(backgroundScope)
        val recovered = fresh.connect()
        fresh.callback.onCharacteristicWriteRequest(fresh.peer, 4,
            fresh.dataInput, false, false, 0, byteArrayOf(0, 9))
        assertContentEquals(byteArrayOf(0, 9), recovered.incoming.receive())
        recovered.close(ProximityCloseReason.COMPLETED)
    }

    @Test fun wrongPeerWritesAndDuplicateTerminationDoNotDiscloseOrRetainResources() = runTest {
        val fixture = Fixture(backgroundScope)
        val raw = fixture.connect()
        fixture.callback.onCharacteristicWriteRequest(fixture.other, 1,
            fixture.dataInput, false, false, 0, byteArrayOf(0, 99))
        assertTrue(raw.incoming.tryReceive().isFailure)
        repeat(2) {
            fixture.callback.onCharacteristicWriteRequest(fixture.peer, 2,
                fixture.role.state, false, false, 0, byteArrayOf(BLE_STATE_END))
        }
        assertTrue(raw.incoming.receiveCatching().isClosed)
        assertTrue(fixture.native.isClosed)
        assertEquals(1, fixture.native.closes)
    }

    private class Fixture(scope: CoroutineScope) {
        private val context = RuntimeEnvironment.getApplication()
        private val manager = context.getSystemService(BluetoothManager::class.java)
        val peer = ShadowBluetoothDevice.newInstance("00:11:22:33:44:55")
        val other = ShadowBluetoothDevice.newInstance("00:11:22:33:44:66")
        val role = AndroidBlePeripheralRole(context, manager, manager.adapter,
            BleServiceUuid.parse("00112233-4455-6677-8899-aabbccddeeff"), false, scope)
        val callback = role.javaClass.getDeclaredField("callback").let {
            it.isAccessible = true
            it.get(role) as BluetoothGattServerCallback
        }
        private val server = manager.openGattServer(context, callback)!!
        val native = Shadow.extract<PendingGattServer>(server)
        val dataInput = BluetoothGattCharacteristic(UUID.fromString(BleGattUuid.MDOC_CLIENT_TO_SERVER), 0, 0)
        init {
            role.javaClass.getDeclaredField("gattServer").apply { isAccessible = true }.set(role, server)
        }
        suspend fun connect(): BleRawConnection {
            callback.onConnectionStateChange(peer, 0, BluetoothProfile.STATE_CONNECTED)
            for (characteristic in listOf(role.state, role.outgoingDataCharacteristic)) {
                callback.onDescriptorWriteRequest(peer, 0, characteristic.descriptors.single(), false, false, 0,
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            }
            callback.onCharacteristicWriteRequest(peer, 0, role.state, false, false, 0, byteArrayOf(BLE_STATE_START))
            return role.awaitConnection()
        }
    }
}

@Implements(BluetoothGattServer::class)
class PendingGattServer : ShadowBluetoothGattServer() {
    val writes = mutableListOf<ByteArray>()
    var closes = 0
    @Implementation
    fun notifyCharacteristicChanged(device: BluetoothDevice, characteristic: BluetoothGattCharacteristic,
        confirm: Boolean, value: ByteArray): Int {
        writes += value.copyOf()
        return 0
    }
    @Implementation
    public override fun close() { closes++; super.close() }
}
