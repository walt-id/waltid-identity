package id.walt.statuslist.compression

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

actual object ZlibCompressor {
    actual fun compress(data: ByteArray): ByteArray {
        val compressor = Deflater(Deflater.BEST_COMPRESSION).apply {
            setInput(data)
            finish()
        }
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!compressor.finished()) {
            val count = compressor.deflate(buffer)
            result.write(buffer, 0, count)
        }
        compressor.end()
        return result.toByteArray()
    }

    actual fun decompress(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count == 0 && inflater.needsInput()) break
            result.write(buffer, 0, count)
        }
        inflater.end()
        return result.toByteArray()
    }
}
