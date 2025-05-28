package id.walt.policies.policies.status.expansion

import id.walt.policies.policies.Base64Utils
import java.io.InputStream
import java.util.zip.InflaterInputStream

class TokenStatusListExpansion : StatusListExpansion {
    override fun expand(bitstring: String): InputStream =
        InflaterInputStream(Base64Utils.urlDecode(bitstring).inputStream())
}