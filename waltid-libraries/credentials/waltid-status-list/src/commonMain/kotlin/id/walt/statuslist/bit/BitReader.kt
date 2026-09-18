package id.walt.statuslist.bit

object BitReader {
    fun readBits(
        input: ByteArray,
        index: Int,
        bitSize: Int,
        bitOrder: BitOrder,
    ): List<Boolean> {
        val bitStartPosition = index.toULong() * bitSize.toUInt()
        val byteStart = (bitStartPosition / 8u).toInt()
        val bytesToRead = (bitSize - 1) / 8 + 1
        val endIndex = minOf(byteStart + bytesToRead, input.size)
        val bytesToProcess = input.sliceArray(byteStart until endIndex)
        val bits = bytesToProcess.toBitSequence(bitOrder)
        val bitStartInSlice = (bitStartPosition % 8u).toInt()
        return bits.drop(bitStartInSlice).take(bitSize).toList()
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
        if (ordered.isEmpty()) return 0u
        return ordered.joinToString("") { if (it) "1" else "0" }.toUInt(2)
    }

    private fun ByteArray.toBitSequence(bitOrder: BitOrder): Sequence<Boolean> =
        fold(emptySequence()) { acc, byte ->
            acc + bitOrder.bitIndices.map { i -> (byte.toUInt() shr i) and 1u == 1u }.asSequence()
        }
}
