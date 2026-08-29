package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.proximity.ImmutableBytes
import id.walt.mdoc.proximity.ProximityException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BleProtocolTest {
    @Test
    fun `service UUID preserves Device Engagement byte order`() {
        val bytes = byteArrayOf(
            0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77,
            0x88.toByte(), 0x99.toByte(), 0xaa.toByte(), 0xbb.toByte(),
            0xcc.toByte(), 0xdd.toByte(), 0xee.toByte(), 0xff.toByte(),
        )

        val uuid = BleServiceUuid.fromBytes(bytes)

        assertEquals("00112233-4455-6677-8899-aabbccddeeff", uuid.toString())
        assertContentEquals(bytes, BleServiceUuid.parse(uuid.toString()).encoded().copy())
        assertFailsWith<IllegalArgumentException> { BleServiceUuid.fromBytes(ByteArray(15)) }
        assertEquals(
            "00112233-4455-6677-0099-aabbccddeeff",
            BleServiceUuid.parse("00112233-4455-6677-0099-aabbccddeeff").toString(),
        )
        assertFailsWith<IllegalArgumentException> { BleServiceUuid.parse("00112233") }
    }

    @Test
    fun `dual roles reject one UUID reused for both advertised services`() {
        val uuid = BleServiceUuid.parse("00112233-4455-6677-8899-aabbccddeeff")

        assertFailsWith<IllegalArgumentException> { BleMdocRoles.Dual(uuid, uuid) }
    }

    @Test
    fun `Ident matches independent HKDF SHA-256 vector`() {
        val actual = BleIdent.derive(ImmutableBytes.of(ByteArray(32) { it.toByte() }))

        assertContentEquals("575e082f5245fb1daacf22a211f2e604".hexToBytes(), actual)
        assertTrue(BleIdent.matches(actual, actual.copyOf()))
        assertFalse(BleIdent.matches(actual, actual.copyOf().also { it[15] = (it[15].toInt() xor 1).toByte() }))
        assertFalse(BleIdent.matches(actual, actual.copyOf(15)))
    }

    @Test
    fun `PSM is encoded as an unsigned big-endian value`() {
        assertContentEquals(byteArrayOf(0x00, 0x00, 0x00, 0x80.toByte()), BlePsmCodec.encode(0x80u))
        assertEquals(0x80u, BlePsmCodec.decode(byteArrayOf(0x00, 0x00, 0x00, 0x80.toByte())))
        assertFailsWith<ProximityException> { BlePsmCodec.decode(byteArrayOf(1, 2)) }
        assertFailsWith<ProximityException> { BlePsmCodec.decode(ByteArray(4)) }
        assertFailsWith<ProximityException> { BlePsmCodec.decode(byteArrayOf(0, 0, 1, 0)) }
        assertFailsWith<IllegalArgumentException> { BlePsmCodec.encode(0x7fu) }
        assertFailsWith<IllegalArgumentException> { BlePsmCodec.encode(0x10000u) }
    }

    @Test
    fun `peripheral state commands enforce Start readiness and terminal End`() {
        var starts = 0

        val end = evaluateBlePeripheralStateCommand(
            value = byteArrayOf(BLE_STATE_END),
            notificationsReady = false,
            start = {
                starts += 1
                true
            },
        )
        val startWithoutSubscriptions = evaluateBlePeripheralStateCommand(
            value = byteArrayOf(BLE_STATE_START),
            notificationsReady = false,
            start = {
                starts += 1
                true
            },
        )
        val readyStart = evaluateBlePeripheralStateCommand(
            value = byteArrayOf(BLE_STATE_START),
            notificationsReady = true,
            start = {
                starts += 1
                true
            },
        )

        assertEquals(BlePeripheralStateCommandResult.TERMINATE, end)
        assertTrue(end.accepted)
        assertEquals(BlePeripheralStateCommandResult.REJECTED, startWithoutSubscriptions)
        assertFalse(startWithoutSubscriptions.accepted)
        assertEquals(BlePeripheralStateCommandResult.STARTED, readyStart)
        assertTrue(readyStart.accepted)
        assertEquals(1, starts)
    }

    @Test
    fun `GATT codec frames at negotiated packet boundary and reassembles`() {
        val codec = BleGattMessageCodec(maximumMessageBytes = 32)
        val message = ByteArray(13) { it.toByte() }

        val chunks = codec.encode(ImmutableBytes.of(message), maximumPacketBytes = 6)

        assertEquals(listOf(6, 6, 4), chunks.map(ByteArray::size))
        assertEquals(listOf(1, 1, 0), chunks.map { it[0].toInt() })
        assertNull(codec.decode(chunks[0]))
        assertTrue(codec.hasIncompleteMessage())
        assertNull(codec.decode(chunks[1]))
        assertContentEquals(message, codec.decode(chunks[2])!!.copy())
        assertFalse(codec.hasIncompleteMessage())
    }

    @Test
    fun `GATT codec handles empty message and resets between messages`() {
        val codec = BleGattMessageCodec(maximumMessageBytes = 4)

        assertContentEquals(ByteArray(0), codec.decode(codec.encode(ImmutableBytes.of(ByteArray(0)), 2).single())!!.copy())
        assertContentEquals(byteArrayOf(9), codec.decode(byteArrayOf(0, 9))!!.copy())
        assertFailsWith<ProximityException> { codec.decode(ByteArray(0)) }
        assertFailsWith<ProximityException> { codec.decode(byteArrayOf(2, 9)) }
        assertFailsWith<ProximityException> { codec.encode(ImmutableBytes.of(ByteArray(5)), 2) }
    }

    @Test
    fun `GATT codec remembers an empty continuation chunk as incomplete`() {
        val codec = BleGattMessageCodec(maximumMessageBytes = 4)

        assertNull(codec.decode(byteArrayOf(1)))
        assertTrue(codec.hasIncompleteMessage())
        assertContentEquals(ByteArray(0), codec.decode(byteArrayOf(0))!!.copy())
        assertFalse(codec.hasIncompleteMessage())
    }

    @Test
    fun `L2CAP codec uses four-byte big-endian length and accepts arbitrary stream splits`() {
        val first = BleL2capMessageCodec.encode(ImmutableBytes.of(byteArrayOf(1, 2, 3)), 16)
        val second = BleL2capMessageCodec.encode(ImmutableBytes.of(byteArrayOf(4, 5)), 16)
        assertContentEquals(byteArrayOf(0, 0, 0, 3, 1, 2, 3), first)
        val decoder = BleL2capMessageDecoder(16)

        assertTrue(decoder.feed((first + second).copyOfRange(0, 2)).isEmpty())
        assertTrue(decoder.hasIncompleteFrame())
        val messages = decoder.feed((first + second).copyOfRange(2, first.size + second.size))

        assertEquals(2, messages.size)
        assertContentEquals(byteArrayOf(1, 2, 3), messages[0].copy())
        assertContentEquals(byteArrayOf(4, 5), messages[1].copy())
        assertFalse(decoder.hasIncompleteFrame())
    }

    @Test
    fun `L2CAP decoder rejects declared size above session limit before allocating it`() {
        val decoder = BleL2capMessageDecoder(8)

        val failure = assertFailsWith<ProximityException> {
            decoder.feed(byteArrayOf(0, 0, 0, 9))
        }

        assertEquals("message_too_large", failure.error.code)
    }
}

private fun String.hexToBytes(): ByteArray = ByteArray(length / 2) { index ->
    substring(index * 2, index * 2 + 2).toInt(16).toByte()
}
