package id.walt.webwallet.utils

import java.io.BufferedReader
import java.io.InputStream
import java.util.*

fun hexToInt(hex: String) = Integer.parseInt(hex.startsWith("0x").takeIf { it }?.let {
    hex.substring(2)
} ?: hex)

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
}

object StreamUtils {
    fun getBitValue(inputStream: InputStream, index: ULong, bitSize: Int) =
        inputStream.bufferedReader().use { buffer ->
            buffer.skip((index * bitSize.toULong()).toLong())
            extractBitValue(buffer, index, bitSize.toULong())
        }

    private fun extractBitValue(it: BufferedReader, index: ULong, bitSize: ULong): List<Char> {
        var int = 0
        var count = index * bitSize
        val result = mutableListOf<Char>()
        while (count < index * bitSize + bitSize) {
            int = it.read().takeIf { it != -1 } ?: error("Reached end of stream")
            result.add(int.toChar())
            count += 1.toULong()
        }
        return result
    }
}