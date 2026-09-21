package id.walt.statuslist.bit

object BitReader {
    fun readBits(
        input: ByteArray,
        index: Int,
        bitSize: Int,
        bitOrder: BitOrder,
    ): List<Boolean> {
        require(index >= 0) { "Status index must be non-negative" }
        require(bitSize > 0) { "bitSize must be positive" }
        val bitStartPosition = index.toULong() * bitSize.toUInt()
        val bitEndPosition = bitStartPosition + bitSize.toUInt()
        val totalBits = input.size.toULong() * 8u
        if (bitEndPosition > totalBits) {
            throw IndexOutOfBoundsException(
                "Status index $index with bitSize $bitSize is outside a ${input.size}-byte list",
            )
        }
        val byteStart = (bitStartPosition / 8u).toInt()
        val bitStartInSlice = (bitStartPosition % 8u).toInt()
        val bytesToRead = (bitStartInSlice + bitSize + 7) / 8
        val bytesToProcess = input.sliceArray(byteStart until byteStart + bytesToRead)
        val bits = bytesToProcess.toBitSequence(bitOrder)
            .drop(bitStartInSlice)
            .take(bitSize)
            .toList()
        require(bits.size == bitSize) {
            "Expected $bitSize status bits at index $index, got ${bits.size}"
        }
        return bits
    }

    fun readStatus(
        input: ByteArray,
        index: Int,
        bitSize: Int,
        bitOrder: BitOrder,
        reverseBits: Boolean = false,
    ): UInt {
        val bits = readBits(input, index, bitSize, bitOrder)
        val ordered = if (reverseBits) bits.reversed() else bits
        return ordered.joinToString("") { if (it) "1" else "0" }.toUInt(2)
    }

    private fun ByteArray.toBitSequence(bitOrder: BitOrder): Sequence<Boolean> =
        fold(emptySequence()) { acc, byte ->
            acc + bitOrder.bitIndices.map { i -> (byte.toUInt() shr i) and 1u == 1u }.asSequence()
        }
}
