@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class)

package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.proximity.ProximityCloseReason
import id.walt.mdoc.proximity.mobile.testdoubles.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import platform.CoreBluetooth.CBPeripheralManager
import platform.Foundation.NSData
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosPeripheralGattCallbackTest {
    @Test fun backpressurePreservesExactPacketOrderAndCancellationDisposesOnce() = onSimulator {
        val fixture = Fixture(backgroundScope)
        val raw = fixture.connect()
        WIDTestSetUpdateReady(fixture.manager, false)
        val sending = async { raw.write(byteArrayOf(1, 2, 3)); raw.write(byteArrayOf(0, 4)) }
        runCurrent()
        assertTrue(WIDTestNotifications(fixture.manager)!!.isEmpty())
        fixture.manager.delegate!!.peripheralManagerIsReadyToUpdateSubscribers(fixture.manager)
        runCurrent()
        assertTrue(sending.isActive, "spurious readiness does not bypass native backpressure")
        WIDTestSetUpdateReady(fixture.manager, true)
        fixture.manager.delegate!!.peripheralManagerIsReadyToUpdateSubscribers(fixture.manager)
        runCurrent()
        sending.await()
        val sent = WIDTestNotifications(fixture.manager)!!
        assertEquals(2, sent.size)
        assertContentEquals(byteArrayOf(1, 2, 3), (sent[0] as NSData).toByteArray())
        assertContentEquals(byteArrayOf(0, 4), (sent[1] as NSData).toByteArray())
        raw.close(ProximityCloseReason.CANCELLED)
        raw.close(ProximityCloseReason.CANCELLED)
        runCurrent()
        assertEquals(1uL, WIDTestRemoveServicesCount(fixture.manager))
    }

    @Test fun unsubscribeUnblocksSendingWithoutWaitingForTheOuterDeadline() = onSimulator {
        val fixture = Fixture(backgroundScope)
        val raw = fixture.connect()
        WIDTestSetUpdateReady(fixture.manager, false)
        val sending = async { runCatching { raw.write(byteArrayOf(0, 1)) } }
        runCurrent()
        val delegate = fixture.manager.delegate!!
        delegate.peripheralManager(fixture.manager, central = WIDTestReaderCentral()!!,
            didUnsubscribeFromCharacteristic = fixture.role.outgoingData)
        runCurrent()
        assertTrue(sending.isActive, "a different central cannot cancel the selected one")
        delegate.peripheralManager(fixture.manager, central = fixture.peer,
            didUnsubscribeFromCharacteristic = fixture.role.outgoingData)
        runCurrent()
        assertTrue(sending.isCompleted, "selected peer loss must unblock the native notification")
        assertTrue(sending.await().isFailure)
        assertTrue(raw.incoming.receiveCatching().isClosed)
        raw.close(ProximityCloseReason.PEER_DISCONNECTED)
        runCurrent()
        assertEquals(1uL, WIDTestRemoveServicesCount(fixture.manager))
        assertTrue(WIDTestNotifications(fixture.manager)!!.isEmpty())
        val fresh = Fixture(backgroundScope)
        val recovered = fresh.connect()
        recovered.write(byteArrayOf(0, 9))
        assertContentEquals(byteArrayOf(0, 9), (WIDTestNotifications(fresh.manager)!!.single() as NSData).toByteArray())
        recovered.close(ProximityCloseReason.COMPLETED)
    }

    @Test fun selectedPeerWritesReachTheChannelAndOtherPeerCannotInjectOrTerminate() = onSimulator {
        val fixture = Fixture(backgroundScope)
        val raw = fixture.connect()
        val delegate = fixture.manager.delegate!!
        val other = WIDTestReaderCentral()!!
        for (characteristic in listOf(WIDTestInput(fixture.manager)!!, fixture.role.state)) {
            delegate.peripheralManager(fixture.manager, didReceiveWriteRequests = listOf(
                WIDTestWriteRequest(other, characteristic, byteArrayOf(BLE_STATE_END).toNSData())!!))
        }
        assertTrue(raw.incoming.tryReceive().isFailure)
        assertTrue(!raw.incoming.isClosedForReceive)
        delegate.peripheralManager(fixture.manager, didReceiveWriteRequests = listOf(
            WIDTestWriteRequest(fixture.peer, WIDTestInput(fixture.manager)!!, byteArrayOf(0, 7, 8).toNSData())!!))
        assertContentEquals(byteArrayOf(0, 7, 8), raw.incoming.receive())
        val end = WIDTestWriteRequest(fixture.peer, fixture.role.state, byteArrayOf(BLE_STATE_END).toNSData())!!
        delegate.peripheralManager(fixture.manager, didReceiveWriteRequests = listOf(end))
        delegate.peripheralManager(fixture.manager, didReceiveWriteRequests = listOf(end))
        runCurrent()
        assertTrue(raw.incoming.receiveCatching().isClosed)
        assertEquals(1uL, WIDTestRemoveServicesCount(fixture.manager))
    }

    private class Fixture(scope: CoroutineScope) {
        lateinit var manager: CBPeripheralManager
        val peer = WIDTestReaderCentral()!!
        val role = IosBlePeripheralRole(BleServiceUuid.parse("00112233-4455-6677-8899-aabbccddeeff"), false, scope) {
            WIDTestPeripheralManager(it)!!.also { manager = it }
        }
        suspend fun connect(): BleRawConnection {
            role.start()
            val delegate = manager.delegate!!
            for (characteristic in listOf(role.state, role.outgoingData)) {
                delegate.peripheralManager(manager, central = peer, didSubscribeToCharacteristic = characteristic)
            }
            delegate.peripheralManager(manager, didReceiveWriteRequests = listOf(
                WIDTestWriteRequest(peer, role.state, byteArrayOf(BLE_STATE_START).toNSData())!!))
            return role.awaitConnection()
        }
    }

    private fun onSimulator(block: suspend TestScope.() -> Unit) {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try { runTest(dispatcher, timeout = 10.seconds) { block() } }
        finally { Dispatchers.resetMain() }
    }
}
