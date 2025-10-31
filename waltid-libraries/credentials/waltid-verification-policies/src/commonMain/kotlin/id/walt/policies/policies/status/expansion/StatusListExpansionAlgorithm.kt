package id.walt.policies.policies.status.expansion

import java.io.InputStream

interface StatusListExpansionAlgorithm {
    operator fun invoke(bitstring: String): InputStream
}