package id.walt.policies.policies.status.expansion

import id.walt.policies.policies.Base64Utils
import java.io.InputStream
import java.util.zip.InflaterInputStream

class RevocationList2020ExpansionAlgorithm : StatusListExpansionAlgorithm {
    override operator fun invoke(bitstring: String): InputStream =
        InflaterInputStream(Base64Utils.decode(bitstring).inputStream())
}