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

    actual fun decompress(data: ByteArray): ByteArray = decompress(data, Int.MAX_VALUE)

    actual fun decompress(data: ByteArray, maxOutputBytes: Int): ByteArray {
        require(maxOutputBytes > 0)
        return GZIPInputStream(ByteArrayInputStream(data)).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > maxOutputBytes - output.size()) throw StatusListSizeLimitException()
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }
}
