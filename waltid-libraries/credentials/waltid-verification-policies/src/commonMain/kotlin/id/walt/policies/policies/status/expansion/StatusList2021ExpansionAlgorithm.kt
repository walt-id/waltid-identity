package id.walt.policies.policies.status.expansion

import id.walt.policies.policies.Base64Utils
import korlibs.io.compression.deflate.GZIP
import korlibs.io.compression.uncompress


class StatusList2021ExpansionAlgorithm : StatusListExpansionAlgorithm {
    override suspend operator fun invoke(bitstring: String): ByteArray =
        GZIP.uncompress(Base64Utils.decode(bitstring))
}