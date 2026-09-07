package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.proximity.ProximityError
import id.walt.mdoc.proximity.ProximityException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class BleIncomingPacketsTest {
    @Test fun overflowDiscardsPartialFragmentsAndClosesBearerOnce() = runTest {
        val packets = Channel<ByteArray>(2)
        var closes = 0
        repeat(2) { assertTrue(packets.offerBlePacket(byteArrayOf(1, it.toByte())) { closes++ }) }
        assertFalse(packets.offerBlePacket(byteArrayOf(0, 3)) { closes++ })
        val failure = assertIs<ProximityException>(packets.receiveCatching().exceptionOrNull())
        assertEquals("ble_receive_overflow", assertIs<ProximityError.Transport>(failure.error).code)
        repeat(10) { assertFalse(packets.offerBlePacket(byteArrayOf(0)) { closes++ }) }
        assertEquals(1, closes)
    }

    @Test fun ownsCallbackBytesAndAllowsFreshConnectionAfterFailure() = runTest {
        val packets = Channel<ByteArray>(1)
        val bytes = byteArrayOf(0, 12)
        assertTrue(packets.offerBlePacket(bytes) { fail("Unexpected overflow") })
        bytes[1] = 99
        assertContentEquals(byteArrayOf(0, 12), packets.receive())
        assertTrue(packets.offerBlePacket(byteArrayOf(0, 13)) { fail("Unexpected overflow") })
        assertContentEquals(byteArrayOf(0, 13), packets.receive())
        packets.close()
        assertFalse(packets.offerBlePacket(bytes) { fail("Late callback is not overflow") })
    }
}
