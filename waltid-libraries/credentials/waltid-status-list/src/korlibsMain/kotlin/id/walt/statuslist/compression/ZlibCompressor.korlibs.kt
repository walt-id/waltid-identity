package id.walt.statuslist.compression

import korlibs.io.compression.deflate.ZLib
import korlibs.io.compression.compress
import korlibs.io.compression.uncompress

actual object ZlibCompressor {
    actual fun compress(data: ByteArray): ByteArray = ZLib.compress(data)
    actual fun decompress(data: ByteArray): ByteArray = ZLib.uncompress(data)
}
