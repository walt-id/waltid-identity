package id.walt.webwallet.service

import id.walt.webwallet.utils.Base64Utils
import id.walt.webwallet.utils.StreamUtils
import java.util.zip.GZIPInputStream

class BitStringValueParser {
    fun get(bitstring: String, idx: ULong? = null, bitSize: Int = 1) =
        idx?.let { StreamUtils.getBitValue(GZIPInputStream(Base64Utils.decode(bitstring).inputStream()), it, bitSize) }
}