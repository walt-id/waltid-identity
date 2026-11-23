package id.walt.statuslist.compression

import korlibs.io.compression.deflate.ZLib
import korlibs.io.compression.compress
import korlibs.io.compression.uncompress

private const val COMPRESS_OUTPUT_HEADROOM = 64

/**
 * korlibs' JS deflater omits a valid stored block for empty input.
 * RFC 1950 empty zlib: header + BFINAL stored block (LEN=0) + Adler-32 of empty data (1).
 */
private val EMPTY_ZLIB = byteArrayOf(
    0x78, 0x01,
    0x01, 0x00, 0x00, 0xff.toByte(), 0xff.toByte(),
    0x00, 0x00, 0x00, 0x01,
)

actual object ZlibCompressor {
    actual fun decompress(data: ByteArray, maximumOutputBytes: Int): ByteArray {
        require(maximumOutputBytes >= 0) { "Maximum decompressed size must be non-negative" }
        require(data.size >= 2) { "Truncated zlib stream" }
        require((data[1].toInt() and 0x20) == 0) { "Zlib dictionary streams are not supported" }
        return ZLib.uncompressBounded(data, maximumOutputBytes)
    }

    actual fun compress(data: ByteArray): ByteArray =
        if (data.isEmpty()) EMPTY_ZLIB.copyOf()
        else ZLib.compress(data, outputSizeHint = data.size + COMPRESS_OUTPUT_HEADROOM)

    actual fun decompress(data: ByteArray): ByteArray = ZLib.uncompress(data)
}
