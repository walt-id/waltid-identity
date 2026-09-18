package id.walt.policies2.vc.policies.status.expansion

import id.walt.statuslist.codec.StatusList2021Codec

class StatusList2021ExpansionAlgorithm : StatusListExpansionAlgorithm {
    override suspend operator fun invoke(bitstring: String): ByteArray =
        StatusList2021Codec.decode(bitstring)
}
