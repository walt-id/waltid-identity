package id.walt.policies.policies.status.expansion

import id.walt.statuslist.codec.TokenStatusListCodec

class TokenStatusListExpansionAlgorithm : StatusListExpansionAlgorithm {
    override suspend operator fun invoke(bitstring: String): ByteArray =
        TokenStatusListCodec.decode(bitstring)
}