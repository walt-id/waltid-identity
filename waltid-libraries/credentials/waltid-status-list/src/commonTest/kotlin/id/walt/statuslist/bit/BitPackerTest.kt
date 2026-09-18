package id.walt.statuslist.bit

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BitPackerTest {
    @Test
    fun `ietf 1-bit packing matches appendix C1`() {
        val statuses = intArrayOf(1, 0, 0, 1, 1, 1, 0, 1, 1, 1, 0, 0, 0, 1, 0, 1)
            .mapIndexed { index, value -> id.walt.statuslist.model.IndexedStatus(index, value.toUInt()) }
        val packed = BitPacker.pack(
            statuses = statuses,
            statusSize = 1,
            bitOrder = BitOrder.LeastSignificantFirst,
            listSizeBytes = 2,
            layout = StatusBitLayout.IetfLittleEndianValue,
        )
        assertContentEquals(byteArrayOf(0xB9.toByte(), 0xA3.toByte()), packed)
    }

    @Test
    fun `ietf 2-bit packing matches appendix C2`() {
        val statuses = intArrayOf(0b01, 0b10, 0b00, 0b11, 0b00, 0b01, 0b00, 0b01, 0b01, 0b10, 0b11, 0b11)
            .mapIndexed { index, value -> id.walt.statuslist.model.IndexedStatus(index, value.toUInt()) }
        val packed = BitPacker.pack(
            statuses = statuses,
            statusSize = 2,
            bitOrder = BitOrder.LeastSignificantFirst,
            listSizeBytes = 3,
            layout = StatusBitLayout.IetfLittleEndianValue,
        )
        assertContentEquals(byteArrayOf(0xC9.toByte(), 0x44.toByte(), 0xF9.toByte()), packed)
    }

    @Test
    fun `w3c big-endian sets the high bit of the first byte`() {
        val packed = BitPacker.pack(
            statuses = listOf(id.walt.statuslist.model.IndexedStatus(0, 1u)),
            statusSize = 1,
            bitOrder = BitOrder.MostSignificantFirst,
            listSizeBytes = 1,
            layout = StatusBitLayout.W3cHexBinary,
        )
        assertEquals(0x80.toByte(), packed[0])
    }

    @Test
    fun `rejects out of range bit index`() {
        assertFailsWith<IndexOutOfBoundsException> {
            BitPacker.setBit(ByteArray(1), 8, BitOrder.LeastSignificantFirst)
        }
    }
}
