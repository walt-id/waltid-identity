package id.walt.statuslist.compression

import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

actual object ZlibCompressor {
    actual fun compress(data: ByteArray): ByteArray {
        val compressor = Deflater(Deflater.BEST_COMPRESSION)
        try {
            compressor.setInput(data)
            compressor.finish()
            val result = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (!compressor.finished()) {
                val count = compressor.deflate(buffer)
                result.write(buffer, 0, count)
            }
            return result.toByteArray()
        } finally {
            compressor.end()
        }
    }

    actual fun decompress(data: ByteArray): ByteArray = decompress(data, Int.MAX_VALUE)

    actual fun decompress(data: ByteArray, maxOutputBytes: Int): ByteArray {
        require(maxOutputBytes > 0)
        val inflater = Inflater()
        try {
            inflater.setInput(data)
            val result = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (!inflater.finished()) {
                val count = try {
                    inflater.inflate(buffer)
                } catch (e: DataFormatException) {
                    throw IllegalArgumentException("Invalid zlib stream", e)
                }
                if (inflater.needsDictionary()) {
                    throw IllegalArgumentException("Zlib dictionary streams are not supported")
                }
                if (count > 0) {
                    if (count > maxOutputBytes - result.size()) throw StatusListSizeLimitException()
                    result.write(buffer, 0, count)
                    continue
                }
                if (inflater.finished()) break
                throw IllegalArgumentException("Incomplete zlib stream")
            }
            require(inflater.finished()) { "Incomplete zlib stream" }
            return result.toByteArray()
        } finally {
            inflater.end()
        }
    }
}
