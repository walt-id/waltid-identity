package id.walt.policies.policies.status.bit

import id.walt.policies.policies.status.expansion.StatusListExpansionAlgorithm
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.InputStream

class BitValueReader(
    private val bitRepresentationStrategy: BitRepresentationStrategy,
) {
    private val BITS_PER_BYTE_UNSIGNED = 8u
    private val logger = KotlinLogging.logger {}

    fun get(
        bitstring: String,
        idx: ULong,
        bitSize: Int = 1,
        expansionAlgorithm: StatusListExpansionAlgorithm,
    ) = getBitValue(expansionAlgorithm(bitstring), idx, bitSize)

    private fun getBitValue(inputStream: InputStream, index: ULong, bitSize: Int): List<Char> =
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

    private fun ByteArray.toBitSequence(): Sequence<Boolean> =
        this.fold(emptySequence<Boolean>()) { acc, byte ->
            acc + bitRepresentationStrategy().map { i -> (byte.toUInt() shr i) and 1u == 1u }.asSequence()
        }

    private fun bitmap(byteArray: ByteArray) = byteArray.map { byte ->
        // Convert byte to binary string
        String.format("%8s", Integer.toBinaryString(byte.toInt() and 0xFF)).replace(' ', '0')
    }
}