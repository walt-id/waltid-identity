package id.walt.policies2.vc.policies.status.bit

import id.walt.policies2.vc.policies.status.expansion.StatusListExpansionAlgorithm
import io.github.oshai.kotlinlogging.KotlinLogging

class BitValueReader(
    private val bitRepresentationStrategy: BitRepresentationStrategy,
) {
    companion object {
        private val BITS_PER_BYTE_UNSIGNED = 8u
    }

    private val logger = KotlinLogging.logger {}

    suspend fun get(
        bitstring: String,
        idx: ULong,
        bitSize: Int = 1,
        expansionAlgorithm: StatusListExpansionAlgorithm,
    ) = getBitValue(expansionAlgorithm(bitstring), idx, bitSize)

    private fun getBitValue(input: ByteArray, index: ULong, bitSize: Int): List<Char> {
        logger.debug { "available bytes: ${bitmap(input)}" }

        // TODO: bitSize constraints
        val bitStartPosition = index * bitSize.toUInt()
        logger.debug { "bitStartPosition overall: $bitStartPosition" }

        val byteStart = bitStartPosition / BITS_PER_BYTE_UNSIGNED
        logger.debug { "skipping: $byteStart bytes" }
        logger.debug { "available: ${input.size - byteStart.toInt()} bytes" }

        val bytesToRead = (bitSize - 1) / BITS_PER_BYTE_UNSIGNED.toInt() + 1
        logger.debug { "readingNext: $bytesToRead bytes" }

        val startIndex = byteStart.toInt()
        val endIndex = minOf(startIndex + bytesToRead, input.size)
        val bytesToProcess = input.sliceArray(startIndex until endIndex)

        return extractBitValue(bytesToProcess, index, bitSize.toUInt())
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
        this.fold(emptySequence()) { acc, byte ->
            acc + bitRepresentationStrategy().map { i -> (byte.toUInt() shr i) and 1u == 1u }.asSequence()
        }

    private fun bitmap(byteArray: ByteArray) = byteArray.map { byte ->
        // Convert byte to binary string
        byte.toUByte().toInt().toString(radix = 2).padStart(8, '0')
    }
}
