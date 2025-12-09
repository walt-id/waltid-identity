package id.walt.policies2.vc.policies.status.expansion

interface StatusListExpansionAlgorithm {
    suspend operator fun invoke(bitstring: String): ByteArray
}
