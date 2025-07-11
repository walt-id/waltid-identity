package id.walt.policies.policies.status.expansion

import id.walt.policies.policies.Base64Utils
import java.io.InputStream
import java.util.zip.GZIPInputStream

class StatusList2021ExpansionAlgorithm : StatusListExpansionAlgorithm {
    override operator fun invoke(bitstring: String): InputStream =
        GZIPInputStream(Base64Utils.decode(bitstring).inputStream())
}