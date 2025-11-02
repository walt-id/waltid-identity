package id.walt.policies2.policies.status.expansion

import id.walt.policies2.policies.Base64Utils
import korlibs.io.compression.deflate.GZIP
import korlibs.io.compression.uncompress


class StatusList2021ExpansionAlgorithm : StatusListExpansionAlgorithm {
    override suspend operator fun invoke(bitstring: String): ByteArray =
        GZIP.uncompress(Base64Utils.decode(bitstring))
}