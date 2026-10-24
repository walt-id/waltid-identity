package id.walt.policies.policies.status.expansion

import id.walt.policies.policies.Base64UrlHandler
import id.walt.statuslist.codec.TokenStatusListCodec

class TokenStatusListExpansionAlgorithm(
    @Suppress("UNUSED_PARAMETER") base64UrlHandler: Base64UrlHandler? = null,
) : StatusListExpansionAlgorithm {
    override suspend operator fun invoke(bitstring: String): ByteArray =
        TokenStatusListCodec.decode(bitstring)
}