@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class)

package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.proximity.mobile.testdoubles.*
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBMutableService
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSData
import platform.Foundation.NSNumber
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosCentralGattCallbackTest {
    @Test fun foreignPeripheralCannotInjectNotificationsOrDisconnectTheSelectedPeer() = runTest(timeout = 10.seconds) {
        val fixture = Fixture()
        fixture.connect(this)
        val delegate = fixture.peer.delegate!!
        val other = WIDTestPeripheral()!!
        fixture.data.setValue(byteArrayOf(0, 99).toNSData())
        delegate.peripheral(other, didUpdateValueForCharacteristic = fixture.data, error = null)
        assertTrue(fixture.session.incoming.tryReceive().isFailure, "foreign notifications must be ignored")
        fixture.central.delegate!!.centralManager(fixture.central, didDisconnectPeripheral = other, error = null)
        assertTrue(!fixture.session.incoming.isClosedForReceive, "foreign disconnect cannot terminate this session")
        fixture.data.setValue(byteArrayOf(0, 1, 2).toNSData())
        delegate.peripheral(fixture.peer, didUpdateValueForCharacteristic = fixture.data, error = null)
        assertContentEquals(byteArrayOf(0, 1, 2), fixture.session.incoming.receive())
        fixture.session.close()
    }

    @Test fun backpressureRequiresTheMatchingReadyCallbackAndAnActuallyWritableQueue() = runTest(timeout = 10.seconds) {
        val fixture = Fixture()
        fixture.connect(this)
        val delegate = fixture.peer.delegate!!
        WIDTestSetWritable(fixture.peer, false)
        WIDTestSetBackpressure(fixture.peer, true)
        val writing = async { fixture.session.write(fixture.output, byteArrayOf(0, 7, 8)) }
        runCurrent()
        assertTrue(WIDTestWrites(fixture.peer)!!.isEmpty())
        WIDTestSetWritable(fixture.peer, true)
        delegate.peripheralIsReadyToSendWriteWithoutResponse(WIDTestPeripheral()!!)
        runCurrent()
        assertTrue(WIDTestWrites(fixture.peer)!!.isEmpty(), "foreign readiness cannot release backpressure")
        delegate.peripheralIsReadyToSendWriteWithoutResponse(fixture.peer)
        runCurrent()
        assertContentEquals(byteArrayOf(0, 7, 8), (WIDTestWrites(fixture.peer)!!.single() as NSData).toByteArray())
        assertTrue(writing.isActive)
        // A duplicate/stale ready callback does not establish that the native queue drained.
        delegate.peripheralIsReadyToSendWriteWithoutResponse(fixture.peer)
        runCurrent()
        assertTrue(writing.isActive, "a ready callback with canSend=false cannot finish the write")
        WIDTestSetWritable(fixture.peer, true)
        delegate.peripheralIsReadyToSendWriteWithoutResponse(fixture.peer)
        runCurrent()
        writing.await()
        fixture.session.close()
    }

    @Test fun disconnectUnblocksSendingDisposesOnceAndFreshSessionRecovers() = runTest(timeout = 10.seconds) {
        val fixture = Fixture()
        fixture.connect(this)
        WIDTestSetWritable(fixture.peer, false)
        val writing = async { runCatching { fixture.session.write(fixture.output, byteArrayOf(0, 1)) } }
        runCurrent()
        val delegate = fixture.central.delegate!!
        delegate.centralManager(fixture.central, didDisconnectPeripheral = fixture.peer, error = null)
        runCurrent()
        assertTrue(writing.await().isFailure)
        assertTrue(fixture.session.incoming.receiveCatching().isClosed)
        fixture.session.close()
        fixture.session.close()
        runCurrent()
        assertEquals(1uL, WIDTestDisconnects(fixture.central))
        assertTrue(WIDTestWrites(fixture.peer)!!.isEmpty())
        val fresh = Fixture()
        fresh.connect(this)
        fresh.session.write(fresh.output, byteArrayOf(0, 2))
        assertContentEquals(byteArrayOf(0, 2), (WIDTestWrites(fresh.peer)!!.single() as NSData).toByteArray())
        fresh.session.close()
    }

    @Test fun readMatchesCharacteristicAndSubscriptionChecksNativeState() = runTest(timeout = 10.seconds) {
        val fixture = Fixture()
        fixture.connect(this)
        val reading = async { fixture.session.read(fixture.ident) }
        runCurrent()
        val other = WIDTestCharacteristic(CBUUID.UUIDWithString(BleGattUuid.READER_L2CAP_PSM), false)!!
        fixture.peer.delegate!!.peripheral(fixture.peer, didUpdateValueForCharacteristic = other, error = null)
        runCurrent()
        assertTrue(reading.isActive)
        fixture.ident.setValue(byteArrayOf(7, 8).toNSData())
        fixture.peer.delegate!!.peripheral(fixture.peer, didUpdateValueForCharacteristic = fixture.ident, error = null)
        runCurrent()
        assertContentEquals(byteArrayOf(7, 8), reading.await())
        val subscribing = async { runCatching { fixture.session.subscribe(other) } }
        runCurrent()
        fixture.peer.delegate!!.peripheral(fixture.peer, didUpdateNotificationStateForCharacteristic = other, error = null)
        runCurrent()
        assertTrue(subscribing.await().isFailure, "a callback alone is insufficient when isNotifying=false")
        fixture.session.close()
    }

    private class Fixture {
        private val uuid = BleServiceUuid.parse("00112233-4455-6677-8899-aabbccddeeff")
        lateinit var central: CBCentralManager
        val session = IosCentralGattSession(uuid) { delegate -> WIDTestCentral(delegate)!!.also { central = it } }
        val peer: CBPeripheral = WIDTestPeripheral()!!
        val state = WIDTestCharacteristic(CBUUID.UUIDWithString(BleGattUuid.READER_STATE), true)!!
        val data = WIDTestCharacteristic(CBUUID.UUIDWithString(BleGattUuid.READER_SERVER_TO_CLIENT), true)!!
        val output = WIDTestCharacteristic(CBUUID.UUIDWithString(BleGattUuid.READER_CLIENT_TO_SERVER), false)!!
        val ident = WIDTestCharacteristic(CBUUID.UUIDWithString(BleGattUuid.READER_IDENT), false)!!
        val service = CBMutableService(CBUUID.UUIDWithString(uuid.platformString()), true).apply {
            setCharacteristics(listOf(state, data, output, ident))
        }
        init { WIDTestSetServices(peer, listOf(service)) }
        suspend fun connect(scope: TestScope) {
            val pending = scope.async { session.connectAndDiscover() }
            scope.runCurrent()
            central.delegate!!.centralManager(central, didDiscoverPeripheral = peer, advertisementData = emptyMap<Any?, Any>(), RSSI = NSNumber(1))
            scope.runCurrent()
            central.delegate!!.centralManager(central, didConnectPeripheral = peer)
            scope.runCurrent()
            peer.delegate!!.peripheral(peer, didDiscoverServices = null)
            scope.runCurrent()
            peer.delegate!!.peripheral(peer, didDiscoverCharacteristicsForService = service, error = null)
            scope.runCurrent()
            pending.await()
        }
    }
}
