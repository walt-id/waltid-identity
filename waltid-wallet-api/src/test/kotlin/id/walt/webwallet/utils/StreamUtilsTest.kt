package id.walt.webwallet.utils

import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class StreamUtilsTest {

    private val bitString = byteArrayOf(0, 1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, 0, 1, 0)

    @Test
    fun `test unit bitsize, no overflow`() {
        val value = StreamUtils.getBitValue(inputStream = bitString.inputStream(), index = 4UL, bitSize = 1)
        assertNotNull(value)
        assertEquals(expected = "0", actual = value.joinToString(""))
    }

    @Test
    fun `test non-unit bitsize, no overflow`() {
        val value = StreamUtils.getBitValue(inputStream = bitString.inputStream(), index = 4UL, bitSize = 2)
        assertNotNull(value)
        assertEquals(expected = "10", actual = value.joinToString(""))
    }

    @Test
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
    fun `test non-unit bitsize, with overflow`() {
        assertFailsWith<IllegalStateException> {
            StreamUtils.getBitValue(
                inputStream = bitString.inputStream(),
                index = 1UL,
                bitSize = 9
            )
        }
    }

    @Test
    fun `just print the entire bitstring`() {
        val bitstring = "H4sIAAAAAAAAA-3BMREAAAgAoQ9ueCdLeECdCQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA4LMFiQj-p6hhAAA"
        val list = mutableListOf<Int>()
        var count = 0
        GZIPInputStream(Base64Utils.urlDecode(bitstring).inputStream()).bufferedReader().use { buffer ->
            var int = 0
            while (int != -1) {
                buffer.read().run {
                    this.takeIf { it != -1 }?.run {
                        list.add(this)
                        count++
                    }
                    int = this
                }
            }
        }
        println(count)
        println(list)
    }
}