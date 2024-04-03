package id.walt.webwallet.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class StreamUtilsTest {

    private val bitString = "0123456789abcdef"

    @Test
    fun `test unit bitsize, no overflow`() {
        val value = StreamUtils.getBitValue(inputStream = bitString.toByteArray().inputStream(), index = 4UL, bitSize = 1)
        assertNotNull(value)
        assertEquals(expected = "4", actual = value.joinToString(""))
    }

    @Test
    fun `test non-unit bitsize, no overflow`() {
        val value = StreamUtils.getBitValue(inputStream = bitString.toByteArray().inputStream(), index = 4UL, bitSize = 2)
        assertNotNull(value)
        assertEquals(expected = "89", actual = value.joinToString(""))
    }

    @Test
    fun `test unit bitsize, with overflow`() {
        assertFailsWith<IllegalStateException> {
            StreamUtils.getBitValue(
                inputStream = bitString.toByteArray().inputStream(),
                index = 16UL,
                bitSize = 1
            )
        }
    }

    @Test
    fun `test non-unit bitsize, with overflow`() {
        assertFailsWith<IllegalStateException> {
            StreamUtils.getBitValue(
                inputStream = bitString.toByteArray().inputStream(),
                index = 1UL,
                bitSize = 9
            )
        }
    }
}