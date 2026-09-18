package id.walt.statuslist.codec

import id.walt.statuslist.StatusListDefaults
import id.walt.statuslist.StatusListFormat
import id.walt.statuslist.bit.BitOrder
import id.walt.statuslist.bit.StatusBitLayout
import id.walt.statuslist.compression.GzipCompressor
import id.walt.statuslist.encoding.StatusListStringEncoding

object BitstringStatusListCodec : AbstractStatusListCodec() {
    override val format: StatusListFormat = StatusListFormat.BitstringStatusList
    override val bitOrder: BitOrder = BitOrder.MostSignificantFirst
    override val layout: StatusBitLayout = StatusBitLayout.W3cHexBinary
    override val stringEncoding: StatusListStringEncoding = StatusListStringEncoding.MultibaseBase64Url
    override val reverseReadBits: Boolean = false

    override fun defaultListSizeBytes(statusSize: Int): Int = StatusListDefaults.W3C_LIST_SIZE_BYTES

    override fun compress(data: ByteArray): ByteArray = GzipCompressor.compress(data)
    override fun decompress(data: ByteArray): ByteArray = GzipCompressor.decompress(data)
}
