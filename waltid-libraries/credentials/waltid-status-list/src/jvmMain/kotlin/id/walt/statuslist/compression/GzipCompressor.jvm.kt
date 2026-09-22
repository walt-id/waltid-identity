package id.walt.statuslist.compression

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

actual object GzipCompressor {
    actual fun compress(data: ByteArray): ByteArray {
        val result = ByteArrayOutputStream()
        GZIPOutputStream(result).use { it.write(data) }
        return result.toByteArray()
    }

    actual fun decompress(data: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
}
