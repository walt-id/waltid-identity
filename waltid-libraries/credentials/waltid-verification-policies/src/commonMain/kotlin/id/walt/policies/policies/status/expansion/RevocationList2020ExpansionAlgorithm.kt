package id.walt.policies.policies.status.expansion

import id.walt.policies.policies.Base64Utils
import korlibs.io.compression.deflate.ZLib
import korlibs.io.compression.uncompress

class RevocationList2020ExpansionAlgorithm : StatusListExpansionAlgorithm {
    override suspend operator fun invoke(bitstring: String): ByteArray =
        ZLib.uncompress(Base64Utils.decode(bitstring))
}