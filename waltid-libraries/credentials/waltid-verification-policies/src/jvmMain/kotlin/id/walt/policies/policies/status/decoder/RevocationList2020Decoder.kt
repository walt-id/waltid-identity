package id.walt.policies.policies.status.decoder

import id.walt.policies.policies.Base64Utils
import java.io.InputStream
import java.util.zip.InflaterInputStream

class RevocationList2020Decoder: StatusListDecoder {
    override fun decode(bitstring: String): InputStream =
        InflaterInputStream(Base64Utils.decode(bitstring).inputStream())
}