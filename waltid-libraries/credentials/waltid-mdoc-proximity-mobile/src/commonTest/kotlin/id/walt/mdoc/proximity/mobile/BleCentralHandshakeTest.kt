package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.proximity.ProximityCloseReason
import id.walt.mdoc.proximity.ProximityException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class BleCentralHandshakeTest {
    private val ident = ByteArray(16) { it.toByte() }
    private val characteristics = BleReaderCharacteristics("state", "send", "receive", "ident", "psm")
    private val gatt = Raw(BleRawBearer.GATT)
    private val l2cap = Raw(BleRawBearer.L2CAP)

    @Test fun gattFallbackPreservesSubscriptionAndStartOrder() = runTest {
        for (hasPsm in listOf(false, true)) {
            val events = mutableListOf<String>()
            val result = establish(events, characteristics.copy(psm = "psm".takeIf { hasPsm }))
            assertSame(gatt, result)
            assertEquals(listOf("read:ident") +
                (if (hasPsm) listOf("read:psm", "open:128") else emptyList()) +
                listOf("subscribe:state", "subscribe:receive", "write:state:1", "gatt"), events)
        }
    }

    @Test fun l2capSkipsGattStartAndDisabledPreferenceSkipsPsm() = runTest {
        val events = mutableListOf<String>()
        assertSame(l2cap, establish(events, opened = l2cap))
        assertEquals(listOf("read:ident", "read:psm", "open:128"), events)
        events.clear()
        assertSame(gatt, establish(events, prefer = false, opened = l2cap))
        assertFalse(events.any { "psm" in it || "open" in it })
    }

    @Test fun invalidIdentAndMalformedPsmFailBeforeSubscription() = runTest {
        val events = mutableListOf<String>()
        assertFailsWith<ProximityException> { establish(events, receivedIdent = ByteArray(16)) }
        assertEquals(listOf("read:ident"), events)
        events.clear()
        assertFailsWith<ProximityException> { establish(events, psmBytes = byteArrayOf()) }
        assertEquals(listOf("read:ident", "read:psm"), events)
    }

    @Test fun cancelledOrFailedOpenDoesNotSilentlyStartGatt() = runTest {
        for (failure in listOf(CancellationException("cancelled"), IllegalStateException("native failure"))) {
            val events = mutableListOf<String>()
            val actual = assertFails { establish(events, openFailure = failure) }
            assertSame(failure, actual)
            assertEquals(listOf("read:ident", "read:psm", "open:128"), events)
        }
    }

    private suspend fun establish(
        events: MutableList<String>,
        chars: BleReaderCharacteristics<String> = characteristics,
        prefer: Boolean = true,
        opened: BleRawConnection? = null,
        receivedIdent: ByteArray = ident,
        psmBytes: ByteArray = BlePsmCodec.encode(128u),
        openFailure: Exception? = null,
    ): BleRawConnection = establishBleCentralBearer(
        chars, ident, prefer,
        read = { events += "read:$it"; if (it == "ident") receivedIdent else psmBytes },
        subscribe = { events += "subscribe:$it" },
        write = { char, bytes -> events += "write:$char:${bytes.single()}" },
        openL2cap = { events += "open:$it"; openFailure?.let { throw it }; opened },
        gatt = { events += "gatt"; gatt },
    )

    private class Raw(override val bearer: BleRawBearer) : BleRawConnection {
        override val incoming = Channel<ByteArray>(1)
        override val maximumGattPacketBytes: Int = 20
        override suspend fun write(bytes: ByteArray) = Unit
        override suspend fun finish() = Unit
        override fun close(reason: ProximityCloseReason) { incoming.close() }
    }
}
