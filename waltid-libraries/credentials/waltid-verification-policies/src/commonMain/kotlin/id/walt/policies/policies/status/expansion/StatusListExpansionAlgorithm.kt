package id.walt.policies.policies.status.expansion

interface StatusListExpansionAlgorithm {
    suspend operator fun invoke(bitstring: String): ByteArray
}