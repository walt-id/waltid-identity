package id.walt.statuslist.bit

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
