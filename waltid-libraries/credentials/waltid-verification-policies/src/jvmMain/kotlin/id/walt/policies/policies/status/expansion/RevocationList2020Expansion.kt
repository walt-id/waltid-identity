package id.walt.policies.policies.status.expansion

import id.walt.policies.policies.Base64Utils
import java.io.InputStream
import java.util.zip.InflaterInputStream

class RevocationList2020Expansion: StatusListExpansion {
    override fun expand(bitstring: String): InputStream =
        InflaterInputStream(Base64Utils.decode(bitstring).inputStream())
}