package id.walt.statuslist.compression

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class BoundedDecompressionTest {
    @Test
    fun gzipAndZlibStopAtTheOutputLimit() {
        val content = ByteArray(128 * 1024) { 42 }
        val gzip = GzipCompressor.compress(content)
        val zlib = ZlibCompressor.compress(content)
        assertFailsWith<StatusListSizeLimitException> { GzipCompressor.decompress(gzip, content.size - 1) }
        assertFailsWith<StatusListSizeLimitException> { ZlibCompressor.decompress(zlib, content.size - 1) }
        assertContentEquals(content, GzipCompressor.decompress(gzip, content.size))
        assertContentEquals(content, ZlibCompressor.decompress(zlib, content.size))
    }
}
