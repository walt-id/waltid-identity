package id.walt.policies.policies.status.decoder

import id.walt.policies.policies.Base64Utils
import java.io.InputStream
import java.util.zip.InflaterInputStream

class TokenStatusListDecoder : StatusListDecoder {
    override fun decode(bitstring: String): InputStream =
        InflaterInputStream(Base64Utils.urlDecode(bitstring).inputStream())
}