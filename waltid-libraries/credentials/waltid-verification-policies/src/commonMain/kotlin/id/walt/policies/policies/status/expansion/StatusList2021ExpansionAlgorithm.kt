package id.walt.policies.policies.status.expansion

import id.walt.crypto.utils.Base64Utils.base64Decode
import korlibs.io.compression.deflate.GZIP
import korlibs.io.compression.uncompress


class StatusList2021ExpansionAlgorithm : StatusListExpansionAlgorithm {
    override suspend operator fun invoke(bitstring: String): ByteArray =
        GZIP.uncompress(bitstring.base64Decode())
}