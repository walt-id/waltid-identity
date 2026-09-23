package id.walt.statuslist.codec

import id.walt.statuslist.StatusListDefaults
import id.walt.statuslist.StatusListFormat
import id.walt.statuslist.bit.BitOrder
import id.walt.statuslist.bit.StatusBitLayout
import id.walt.statuslist.compression.ZlibCompressor
import id.walt.statuslist.encoding.StatusListStringEncoding

object TokenStatusListCodec : AbstractStatusListCodec() {
    override val format: StatusListFormat = StatusListFormat.TokenStatusList
    override val bitOrder: BitOrder = BitOrder.LeastSignificantFirst
    override val layout: StatusBitLayout = StatusBitLayout.IetfLittleEndianValue
    override val stringEncoding: StatusListStringEncoding = StatusListStringEncoding.Base64Url
    override val reverseReadBits: Boolean = true

    override fun defaultListSizeBytes(statusSize: Int): Int =
        StatusListDefaults.ietfListSizeBytes(statusSize)

    override fun compress(data: ByteArray): ByteArray = ZlibCompressor.compress(data)
    override fun decompress(data: ByteArray): ByteArray = ZlibCompressor.decompress(data)
}
