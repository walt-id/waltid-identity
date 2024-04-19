package id.walt.webwallet.utils

import java.io.BufferedReader
import java.io.InputStream
import java.util.*

fun hexToInt(hex: String) = hex.startsWith("0x").takeIf { it }?.let {
    Integer.parseInt(hex.substring(2))
} ?: Integer.parseInt(hex)

fun binToInt(bin: String) = Integer.parseInt(bin, 2)

object HttpUtils {
    fun parseQueryParam(query: String) = query.split("&").mapNotNull {
        it.split("=").takeIf {
            it.size > 1
        }?.let {
            Pair(it[0], it[1])
        }
    }.associate {
        it.first to it.second
    }
}

object Base64Utils {
    fun decode(base64: String): ByteArray = Base64.getDecoder().decode(base64)
    fun urlDecode(base64Url: String): ByteArray = Base64.getUrlDecoder().decode(base64Url)
}

object StreamUtils {
    fun getBitValue(inputStream: InputStream, index: ULong, bitSize: Int): List<Char> =
        inputStream.use { stream ->
            val bitSet = BitSet.valueOf(stream.readAllBytes())//TODO: !!potential overflow (2GB limit)
            val result = mutableListOf<Char>()
            for (i in index.toInt()..<index.toInt() + bitSize) {
                val c = bitSet[i].takeIf { it }?.let { 1 } ?: 0
                result.add(c.digitToChar())
            }
            result
        }

    private fun extractBitValue(it: BufferedReader, index: ULong, bitSize: ULong): List<Char> {
        var int = 0
        var count = index * bitSize
        val result = mutableListOf<Char>()
        while (count < index * bitSize + bitSize) {
            int = it.read().takeIf { it != -1 } ?: error("Reached end of stream")
            result.add(int.digitToChar())
            count += 1.toULong()
        }
        return result
    }
}