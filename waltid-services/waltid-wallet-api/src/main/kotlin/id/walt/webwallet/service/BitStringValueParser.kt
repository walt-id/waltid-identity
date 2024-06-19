package id.walt.webwallet.service

import id.walt.webwallet.utils.Base64Utils
import id.walt.webwallet.utils.BitstringUtils
import java.util.zip.GZIPInputStream

class BitStringValueParser {
    fun get(bitstring: String, idx: ULong? = null, bitSize: Int = 1) =
        idx?.let { BitstringUtils.getBitValue(GZIPInputStream(Base64Utils.urlDecode(bitstring).inputStream()), it, bitSize) }
}