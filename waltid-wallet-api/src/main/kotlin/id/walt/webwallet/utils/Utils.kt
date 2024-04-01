package id.walt.webwallet.utils

import java.io.BufferedReader
import java.util.*
import java.util.zip.GZIPInputStream

fun hexToInt(hex: String) = Integer.parseInt(hex.startsWith("0x").takeIf { it }?.let {
    hex.substring(2)
} ?: hex)

object Base64Utils {
    fun decode(base64: String): ByteArray = Base64.getDecoder().decode(base64)
}

object GzipUtils {
    fun uncompress(data: ByteArray, idx: ULong? = null, bitSize: Int) =
        GZIPInputStream(data.inputStream()).bufferedReader().use { buffer ->
            idx?.let { index ->
                seekStartPosition(index, buffer, bitSize).takeIf { it != -1 }?.let {
                    extractBitValue(buffer, bitSize)
                }
            }
        }

    private fun seekStartPosition(index: ULong, it: BufferedReader, bitSize: Int): Int {
        var int = it.read()
        var count = 0UL
        while (int != -1 && count < index) {
            int = it.read()
            count += bitSize.toULong()
        }
        return int
    }

    private fun extractBitValue(it: BufferedReader, bitSize: Int): List<Char> {
        var int = it.read()
        var count = 0UL
        val result = mutableListOf<Char>()
//        while (int != -1 && count++ < bitSize.toULong()) {
        while (count++ < bitSize.toULong()) {
            int = it.read()
            result.add(int.toChar())
        }
        return result
    }
}