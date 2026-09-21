package id.walt.statuslist.compression

import korlibs.io.compression.deflate.GZIP
import korlibs.io.compression.compress
import korlibs.io.compression.uncompress

private const val COMPRESS_OUTPUT_HEADROOM = 64

/**
 * korlibs' JS deflater omits a valid stored block for empty input.
 * RFC 1952 empty gzip: header + BFINAL stored block (LEN=0) + crc32 + isize.
 */
private val EMPTY_GZIP = byteArrayOf(
    0x1f, 0x8b.toByte(), 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xff.toByte(),
    0x01, 0x00, 0x00, 0xff.toByte(), 0xff.toByte(),
    0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00,
)

actual object GzipCompressor {
    actual fun compress(data: ByteArray): ByteArray =
        if (data.isEmpty()) EMPTY_GZIP.copyOf()
        else GZIP.compress(data, outputSizeHint = data.size + COMPRESS_OUTPUT_HEADROOM)

    actual fun decompress(data: ByteArray): ByteArray = GZIP.uncompress(data)
}
