package id.walt.statuslist.compression

import id.walt.statuslist.codec.TokenStatusListCodec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

class ZlibCompressorNegativeTest {
    @Test
    fun `rejects a zlib stream that would previously hang inflate`() {
        assertFails {
            TokenStatusListCodec.decode("eLsADQAHYwQjAAAsAA0")
        }
    }

    @Test
    fun `rejects a zlib stream with the checksum trailer removed`() {
        val compressed = ZlibCompressor.compress(byteArrayOf(1, 2, 3, 4))
        val truncated = compressed.copyOf(compressed.size - 4)
        assertFailsWith<IllegalArgumentException> {
            ZlibCompressor.decompress(truncated)
        }
    }

    @Test
    fun `round-trips nonempty input after the inflater finally block`() {
        val input = byteArrayOf(9, 8, 7, 6)
        assertContentEquals(input, ZlibCompressor.decompress(ZlibCompressor.compress(input)))
    }
}
