package id.walt.statuslist.bit

import id.walt.statuslist.model.IndexedStatus
import kotlin.experimental.or

enum class StatusBitLayout {
    IetfLittleEndianValue,
    W3cHexBinary,
}

object BitPacker {
    fun pack(
        statuses: List<IndexedStatus>,
        statusSize: Int,
        bitOrder: BitOrder,
        listSizeBytes: Int,
        layout: StatusBitLayout,
    ): ByteArray {
        require(statusSize > 0) { "statusSize must be positive" }
        val array = ByteArray(listSizeBytes)
        statuses.forEach { status ->
            val offset = status.index * statusSize
            bitOffsets(status, statusSize, layout).forEach { setBit(array, offset + it, bitOrder) }
        }
        return array
    }

    fun setBit(array: ByteArray, index: Int, bitOrder: BitOrder) {
        if (index < 0 || index >= array.size * 8) {
            throw IndexOutOfBoundsException("Bit index is out of range")
        }
        val byteIndex = index / 8
        val bitIndex = when (bitOrder) {
            BitOrder.LeastSignificantFirst -> index % 8
            BitOrder.MostSignificantFirst -> 7 - (index % 8)
        }
        array[byteIndex] = array[byteIndex] or (1 shl bitIndex).toByte()
    }

    private fun bitOffsets(status: IndexedStatus, statusSize: Int, layout: StatusBitLayout): List<Int> =
        when (layout) {
            StatusBitLayout.IetfLittleEndianValue -> {
                val statusValue = status.value.toInt()
                require(statusValue < (1 shl statusSize)) {
                    "Status value 0x${status.value.toString(16)} does not fit into $statusSize bits"
                }
                (0 until statusSize).filter { bitIndex -> (statusValue shr bitIndex) and 1 == 1 }
            }

            StatusBitLayout.W3cHexBinary -> {
                val binaryString = status.value.toInt().toString(2)
                binaryString.indices.filter { binaryString[it] != '0' }
            }
        }
}
