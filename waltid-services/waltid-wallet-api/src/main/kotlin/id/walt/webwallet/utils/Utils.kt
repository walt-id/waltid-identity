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
    private const val BITS_PER_BYTE_UNSIGNED = 8u

    fun getBitValue(inputStream: InputStream, index: ULong, bitSize: Int): List<Char> =
        inputStream.use { stream ->
            if (stream.markSupported()) {
                stream.mark(Int.MAX_VALUE)
                logger.debug { "available bytes: ${bitmap(stream.readAllBytes())}" }
                inputStream.reset()
            }
            //TODO: bitSize constraints
            val bitStartPosition = index * bitSize.toUInt()
            logger.debug { "bitStartPosition overall: $bitStartPosition" }
            val byteStart = bitStartPosition / BITS_PER_BYTE_UNSIGNED
            logger.debug { "skipping: $byteStart bytes" }
            stream.skip(byteStart.toLong())
            logger.debug { "available: ${stream.available()} bytes" }
            val bytesToRead = (bitSize - 1) / BITS_PER_BYTE_UNSIGNED.toInt() + 1
            logger.debug { "readingNext: $bytesToRead bytes" }
            extractBitValue(stream.readNBytes(bytesToRead), index, bitSize.toUInt())
        }

    private fun extractBitValue(bytes: ByteArray, index: ULong, bitSize: UInt): List<Char> {
        logger.debug { "selected byte: ${bitmap(bytes)}" }
        val bits = bytes.toBitSequence()
        val bitStartPosition = index * bitSize % BITS_PER_BYTE_UNSIGNED
        logger.debug { "bitStartPosition within byte: $bitStartPosition" }
        val bitSet = bits.drop(bitStartPosition.toInt()).iterator()
        val result = mutableListOf<Boolean>()
        var b = 0u
        while (b++ < bitSize) {
            result.add(bitSet.next())
        }
        return result.map { if (it) '1' else '0' }
    }

    fun ByteArray.toBitSequence(): Sequence<Boolean> = this.fold(emptySequence<Boolean>()) { acc, byte ->
        acc + (7 downTo 0).map { i -> (byte.toUInt() shr i) and 1u == 1u }.asSequence()
    }

    fun bitmap(byteArray: ByteArray) = byteArray.map { byte ->
        // Convert byte to binary string
        String.format("%8s", Integer.toBinaryString(byte.toInt() and 0xFF)).replace(' ', '0')
    }
}
