package id.walt.webwallet.utils

import kotlin.test.*

class StreamUtilsTest {
    private val bitString = byteArrayOf(0b0101010110101010.toByte())

    @Test
    fun `test unit bitsize, no overflow`() {
        val value = StreamUtils.getBitValue(inputStream = bitString.inputStream(), index = 4UL, bitSize = 1)
        assertNotNull(value)
        assertEquals(expected = "0", actual = value.joinToString(""))
    }

    @Test
    fun `test non-unit bitsize, no overflow`() {
        val value = StreamUtils.getBitValue(inputStream = bitString.inputStream(), index = 4UL, bitSize = 3)
        assertNotNull(value)
        assertEquals(expected = "010", actual = value.joinToString(""))
    }

    @Test
    @Ignore
    fun `test unit bitsize, with overflow`() {
        assertFailsWith<IllegalStateException> {
            StreamUtils.getBitValue(
                inputStream = bitString.inputStream(),
                index = 16UL,
                bitSize = 1
            )
        }
    }

    @Test
    @Ignore
    fun `test non-unit bitsize, with overflow`() {
        assertFailsWith<IllegalStateException> {
            StreamUtils.getBitValue(
                inputStream = bitString.inputStream(),
                index = 1UL,
                bitSize = 9
            )
        }
    }
}