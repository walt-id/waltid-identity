package id.walt.webwallet.utils

import id.walt.webwallet.utils.StringUtils.hexToByteArray
import kotlin.test.*

class BitstringUtilsTest {
    private val bitString = hexToByteArray("A265")//0b10100010_01100101

    @Test
    fun validateUnitBitsizeNoOverflow() {
        val value = BitstringUtils.getBitValue(inputStream = bitString.inputStream(), index = 6UL, bitSize = 1)
        assertNotNull(value)
        assertEquals(expected = "1", actual = value.joinToString(""))
    }

    @Test
    fun validateNonUnitBitsizeNoOverflow() {
        val value = BitstringUtils.getBitValue(inputStream = bitString.inputStream(), index = 4UL, bitSize = 3)
        assertNotNull(value)
        assertEquals(expected = "010", actual = value.joinToString(""))
    }

    @Test
    @Ignore
    fun validateUnitBitsizeWithOverflow() {
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
    fun validateNonUnitBitsizeWithOverflow() {
        assertFailsWith<IllegalStateException> {
            BitstringUtils.getBitValue(
                inputStream = bitString.inputStream(),
                index = 1UL,
                bitSize = 9
            )
        }
    }
}