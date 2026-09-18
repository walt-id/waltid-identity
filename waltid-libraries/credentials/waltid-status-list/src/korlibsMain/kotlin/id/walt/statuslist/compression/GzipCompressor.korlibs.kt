package id.walt.statuslist.compression

import korlibs.io.compression.deflate.GZIP
import korlibs.io.compression.compress
import korlibs.io.compression.uncompress

actual object GzipCompressor {
    actual fun compress(data: ByteArray): ByteArray = GZIP.compress(data)
    actual fun decompress(data: ByteArray): ByteArray = GZIP.uncompress(data)
}
