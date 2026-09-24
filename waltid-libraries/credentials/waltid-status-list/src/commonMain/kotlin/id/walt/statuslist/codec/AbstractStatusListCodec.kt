package id.walt.statuslist.codec

import id.walt.statuslist.bit.BitOrder
import id.walt.statuslist.bit.BitPacker
import id.walt.statuslist.bit.BitReader
import id.walt.statuslist.bit.StatusBitLayout
import id.walt.statuslist.encoding.StatusListEncoding
import id.walt.statuslist.encoding.StatusListStringEncoding
import id.walt.statuslist.model.IndexedStatus

abstract class AbstractStatusListCodec : StatusListCodec {
    protected abstract val bitOrder: BitOrder
    protected abstract val layout: StatusBitLayout
    protected abstract val stringEncoding: StatusListStringEncoding
    protected abstract val reverseReadBits: Boolean

    protected abstract fun compress(data: ByteArray): ByteArray
    protected abstract fun decompress(data: ByteArray): ByteArray

    override fun encode(
        statuses: List<IndexedStatus>,
        statusSize: Int,
        listSizeBytes: Int,
    ): String = StatusListEncoding.encode(encodeBytes(statuses, statusSize, listSizeBytes), stringEncoding)

    override fun encodeBytes(
        statuses: List<IndexedStatus>,
        statusSize: Int,
        listSizeBytes: Int,
    ): ByteArray = compress(BitPacker.pack(statuses, statusSize, bitOrder, listSizeBytes, layout))

    override fun encode(byteArray: ByteArray): String =
        StatusListEncoding.encode(compress(byteArray), stringEncoding)

    override fun decode(encoded: String): ByteArray =
        decompress(StatusListEncoding.decode(encoded, stringEncoding))

    override fun readStatus(decoded: ByteArray, index: Int, bits: Int): UInt =
        BitReader.readStatus(decoded, index, bits, bitOrder, reverseReadBits)
}
