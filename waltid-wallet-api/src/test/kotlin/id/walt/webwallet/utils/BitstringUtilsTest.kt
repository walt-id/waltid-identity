package id.walt.webwallet.utils

import id.walt.webwallet.utils.StringUtils.hexToByteArray
import kotlin.test.*

class BitstringUtilsTest {
    private val bitString = hexToByteArray("5AA5")//0b01011010_10100101

    @Test
    fun `test unit bitsize, no overflow`() {
        val value = BitstringUtils.getBitValue(inputStream = bitString.inputStream(), index = 6UL, bitSize = 1)
        assertNotNull(value)
        assertEquals(expected = "1", actual = value.joinToString(""))
    }

    @Test
    fun `test non-unit bitsize, no overflow`() {
        val value = BitstringUtils.getBitValue(inputStream = bitString.inputStream(), index = 4UL, bitSize = 3)
        assertNotNull(value)
        assertEquals(expected = "010", actual = value.joinToString(""))
    }

    @Test
    @Ignore
    fun `test unit bitsize, with overflow`() {
        assertFailsWith<IllegalStateException> {
            BitstringUtils.getBitValue(
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
            BitstringUtils.getBitValue(
                inputStream = bitString.inputStream(),
                index = 1UL,
                bitSize = 9
            )
        }
    }
}