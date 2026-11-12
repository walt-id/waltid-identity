package id.walt.policies2.vc.policies.status.expansion

import id.walt.statuslist.codec.RevocationList2020Codec

class RevocationList2020ExpansionAlgorithm : StatusListExpansionAlgorithm {
    override suspend operator fun invoke(bitstring: String): ByteArray =
        RevocationList2020Codec.decode(bitstring)
}
