package id.walt.policies2.vc.policies.status.expansion

import id.walt.statuslist.codec.BitstringStatusListCodec

class BitstringStatusListExpansionAlgorithm(
    @Suppress("UNUSED_PARAMETER") base64UrlHandler: id.walt.policies2.vc.policies.status.Base64UrlHandler? = null,
) : StatusListExpansionAlgorithm {
    override suspend operator fun invoke(bitstring: String): ByteArray =
        BitstringStatusListCodec.decode(bitstring)
}
