package id.walt.statuslist.compression

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class CompressionRoundTripTest {
    @Test
    fun `gzip round-trips empty, small, and larger arrays`() {
        listOf(ByteArray(0), byteArrayOf(1, 2, 3), ByteArray(4096) { it.toByte() }).forEach { input ->
            assertContentEquals(input, GzipCompressor.decompress(GzipCompressor.compress(input)))
        }
    }

    @Test
    fun `zlib round-trips empty, small, and larger arrays`() {
        listOf(ByteArray(0), byteArrayOf(1, 2, 3), ByteArray(4096) { it.toByte() }).forEach { input ->
            val compressed = ZlibCompressor.compress(input)
            assertTrue(compressed.isNotEmpty() || input.isEmpty())
            assertContentEquals(input, ZlibCompressor.decompress(compressed))
        }
    }
}
