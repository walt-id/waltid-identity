package id.walt.policies.policies.status.decoder

import id.walt.policies.policies.Base64Utils
import java.io.InputStream
import java.util.zip.GZIPInputStream

class BitstringStatusListDecoder : StatusListDecoder {
    override fun decode(bitstring: String): InputStream = GZIPInputStream(Base64Utils.urlDecode(bitstring).inputStream())
}