package id.walt.statuslist.bit

import id.walt.statuslist.codec.BitstringStatusListCodec
import id.walt.statuslist.codec.TokenStatusListCodec
import id.walt.statuslist.model.IndexedStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BitReaderTest {
    @Test
    fun `reads little-endian 1-bit values from appendix C1 bytes`() {
        val bytes = byteArrayOf(0xB9.toByte(), 0xA3.toByte())
        val expected = intArrayOf(1, 0, 0, 1, 1, 1, 0, 1, 1, 1, 0, 0, 0, 1, 0, 1)
        expected.forEachIndexed { index, value ->
            assertEquals(
                value.toUInt(),
                BitReader.readStatus(bytes, index, 1, BitOrder.LeastSignificantFirst, reverseBits = true),
            )
        }
    }

    @Test
    fun `reads little-endian 2-bit values from appendix C2 bytes`() {
        val bytes = byteArrayOf(0xC9.toByte(), 0x44.toByte(), 0xF9.toByte())
        val expected = intArrayOf(0b01, 0b10, 0b00, 0b11, 0b00, 0b01, 0b00, 0b01, 0b01, 0b10, 0b11, 0b11)
        expected.forEachIndexed { index, value ->
            assertEquals(
                value.toUInt(),
                BitReader.readStatus(bytes, index, 2, BitOrder.LeastSignificantFirst, reverseBits = true),
            )
        }
    }

    @Test
    fun `reads big-endian 1-bit values`() {
        val bytes = byteArrayOf(0x80.toByte())
        assertEquals(1u, BitReader.readStatus(bytes, 0, 1, BitOrder.MostSignificantFirst))
        assertEquals(0u, BitReader.readStatus(bytes, 1, 1, BitOrder.MostSignificantFirst))
    }

    @Test
    fun `rejects the first index beyond a one-byte list`() {
        assertFailsWith<IndexOutOfBoundsException> {
            TokenStatusListCodec.readStatus(byteArrayOf(0), index = 8, bits = 1)
        }
    }

    @Test
    fun `reads a three-bit w3c value that spans two bytes`() {
        val packed = BitPacker.pack(
            statuses = listOf(IndexedStatus(2, 7u)),
            statusSize = 3,
            bitOrder = BitOrder.MostSignificantFirst,
            listSizeBytes = 2,
            layout = StatusBitLayout.W3cHexBinary,
        )
        assertEquals(7u, BitReader.readStatus(packed, 2, 3, BitOrder.MostSignificantFirst))
        assertEquals(7u, BitstringStatusListCodec.readStatus(packed, 2, 3))
        // [0x01, 0xC0] at index 2 / 3 bits used to return 1u because only one byte was read.
        // MSB-first bits 6-8 of that fixture are 011, so the complete read is 3.
        assertEquals(3u, BitstringStatusListCodec.readStatus(byteArrayOf(0x01, 0xC0.toByte()), 2, 3))
    }
}
