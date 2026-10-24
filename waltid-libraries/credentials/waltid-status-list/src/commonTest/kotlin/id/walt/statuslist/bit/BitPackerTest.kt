package id.walt.statuslist.bit

import kotlin.test.Test
import kotlin.test.assertContentEquals
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
    fun `w3c values preserve their declared width`() {
        fun assertPacked(value: UInt, bits: Int, index: Int, expected: ByteArray) {
            val packed = BitPacker.pack(
                statuses = listOf(id.walt.statuslist.model.IndexedStatus(index, value)),
                statusSize = bits,
                bitOrder = BitOrder.MostSignificantFirst,
                listSizeBytes = expected.size,
                layout = StatusBitLayout.W3cHexBinary,
            )
            assertContentEquals(expected, packed, "value=$value, bits=$bits, index=$index")
        }

        assertPacked(1u, 1, 0, byteArrayOf(0x80.toByte()))
        assertPacked(0u, 3, 2, byteArrayOf(0x00, 0x00))
        assertPacked(1u, 3, 2, byteArrayOf(0x00, 0x80.toByte()))
        assertPacked(3u, 3, 2, byteArrayOf(0x01, 0x80.toByte()))
        assertPacked(0x80000000u, 32, 0, byteArrayOf(0x80.toByte(), 0, 0, 0))
        assertPacked(UInt.MAX_VALUE, 32, 0, byteArrayOf(-1, -1, -1, -1))
    }

    @Test
    fun `w3c values cannot spill into the next entry`() {
        assertFailsWith<IllegalArgumentException> {
            BitPacker.pack(
                statuses = listOf(id.walt.statuslist.model.IndexedStatus(2, 9u)),
                statusSize = 3,
                bitOrder = BitOrder.MostSignificantFirst,
                listSizeBytes = 2,
                layout = StatusBitLayout.W3cHexBinary,
            )
        }
    }

    @Test
    fun `rejects out of range bit index`() {
        assertFailsWith<IndexOutOfBoundsException> {
            BitPacker.setBit(ByteArray(1), 8, BitOrder.LeastSignificantFirst)
        }
    }
}
