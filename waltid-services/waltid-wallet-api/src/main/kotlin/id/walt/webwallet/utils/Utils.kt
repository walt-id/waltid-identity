package id.walt.webwallet.utils

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.InputStream
import java.util.*

@OptIn(ExperimentalStdlibApi::class)
object StringUtils {
    fun hexToInt(hex: String) = hex.removePrefix("0x").hexToInt()
    fun hexToByteArray(hex: String) = hex.removePrefix("0x").hexToByteArray()
    fun binToInt(bin: String) = bin.toInt(2)


    fun String.couldBeJsonObject() = trim().let { s -> s.startsWith("{") && s.endsWith("}") }
    fun String.parseAsJsonObject() = runCatching { Json.parseToJsonElement(this).jsonObject }
}

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

object BitstringUtils {
    private val logger = KotlinLogging.logger {}
    fun getBitValue(inputStream: InputStream, index: ULong, bitSize: Int): List<Char> =
        inputStream.use { stream ->
            //TODO: bitSize constraints
            val bitStartPosition = index * bitSize.toUInt()
            logger.debug { "bitStartPosition: $bitStartPosition" }
            val byteStart = bitStartPosition / 8u
            logger.debug { "skipping: $byteStart bytes" }
            stream.skip(byteStart.toLong())
            logger.debug { "available: ${stream.available()} bytes" }
            val bytesToRead = (bitSize - 1) / 8 + 1
            logger.debug { "readingNext: $bytesToRead bytes" }
            extractBitValue(stream.readNBytes(bytesToRead), index, bitSize.toUInt())
        }

    private fun extractBitValue(bytes: ByteArray, index: ULong, bitSize: UInt): List<Char> {
        val bitSet = BitSet.valueOf(bytes)
        logger.debug { "bits set: ${bitSet.length()}" }
        val bitStart = index * bitSize % 8u
        logger.debug { "startingFromBit: $bitStart" }
        val result = mutableListOf<Char>()
        for (i in bitStart..<bitStart + bitSize) {
            val b = bitSet[i.toInt()].takeIf { it }?.let { 1 } ?: 0
            result.add(b.digitToChar())
        }
        return result
    }
}
