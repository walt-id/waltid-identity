package id.walt.statuslist.compression

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class CompressionRoundTripTest {
    @Test
    fun `gzip round-trips empty small and larger arrays`() {
        listOf(ByteArray(0), byteArrayOf(1, 2, 3), ByteArray(4096) { it.toByte() }).forEach { input ->
            assertContentEquals(input, GzipCompressor.decompress(GzipCompressor.compress(input)))
        }
    }

    @Test
    fun `zlib round-trips empty small and larger arrays`() {
        listOf(ByteArray(0), byteArrayOf(1, 2, 3), ByteArray(4096) { it.toByte() }).forEach { input ->
            val compressed = ZlibCompressor.compress(input)
            assertTrue(compressed.isNotEmpty() || input.isEmpty())
            assertContentEquals(input, ZlibCompressor.decompress(compressed))
        }
    }

    @Test
    fun `mutating one empty gzip result does not affect the next`() {
        GzipCompressor.compress(byteArrayOf()).fill(0)
        assertContentEquals(byteArrayOf(), GzipCompressor.decompress(GzipCompressor.compress(byteArrayOf())))
    }

    @Test
    fun `mutating one empty zlib result does not affect the next`() {
        ZlibCompressor.compress(byteArrayOf()).fill(0)
        assertContentEquals(byteArrayOf(), ZlibCompressor.decompress(ZlibCompressor.compress(byteArrayOf())))
    }
}
