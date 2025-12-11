package id.walt.policies2.vc.policies.status.expansion

import id.walt.crypto.utils.Base64Utils.base64Decode
import korlibs.io.compression.deflate.ZLib
import korlibs.io.compression.uncompress

class RevocationList2020ExpansionAlgorithm : id.walt.policies2.vc.policies.status.expansion.StatusListExpansionAlgorithm {
    override suspend operator fun invoke(bitstring: String): ByteArray =
        ZLib.uncompress(bitstring.base64Decode())
}
