package id.walt.statuslist.codec

import id.walt.statuslist.StatusListDefaults
import id.walt.statuslist.StatusListFormat
import id.walt.statuslist.bit.BitOrder
import id.walt.statuslist.bit.StatusBitLayout
import id.walt.statuslist.compression.ZlibCompressor
import id.walt.statuslist.encoding.StatusListStringEncoding

object RevocationList2020Codec : AbstractStatusListCodec() {
    override val format: StatusListFormat = StatusListFormat.RevocationList2020
    override val bitOrder: BitOrder = BitOrder.MostSignificantFirst
    override val layout: StatusBitLayout = StatusBitLayout.W3cHexBinary
    override val stringEncoding: StatusListStringEncoding = StatusListStringEncoding.Base64
    override val reverseReadBits: Boolean = false

    override fun defaultListSizeBytes(statusSize: Int): Int = StatusListDefaults.W3C_LIST_SIZE_BYTES

    override fun compress(data: ByteArray): ByteArray = ZlibCompressor.compress(data)
    override fun decompress(data: ByteArray): ByteArray = ZlibCompressor.decompress(data)
}
