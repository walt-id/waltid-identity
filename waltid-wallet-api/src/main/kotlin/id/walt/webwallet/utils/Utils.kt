package id.walt.webwallet.utils

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
            //TODO: bitSize constraints
            val bitStartPosition = index * bitSize.toUInt()
            println("bitStartPosition: $bitStartPosition")
            val byteStart = bitStartPosition / 8u
            println("skipping: $byteStart bytes")
            stream.skip(byteStart.toLong())
            println("available: ${stream.available()} bytes")
            val bytesToRead = (bitSize - 1) / 8 + 1
            println("readingNext: $bytesToRead bytes")
            extractBitValue(stream.readNBytes(bytesToRead), index, bitSize.toUInt())
        }

    private fun extractBitValue(bytes: ByteArray, index: ULong, bitSize: UInt): List<Char> {
        val bitSet = BitSet.valueOf(bytes)
        println("bits set: ${bitSet.length()}")
        val bitStart = index * bitSize % 8u
        println("startingFromBit: $bitStart")
        val result = mutableListOf<Char>()
        for (i in bitStart..<bitStart + bitSize) {
            val c = bitSet[i.toInt()].takeIf { it }?.let { 1 } ?: 0
            result.add(c.digitToChar())
        }
        return result
    }
}