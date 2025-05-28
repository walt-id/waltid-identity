package id.walt.policies.policies.status.expansion

import java.io.InputStream

interface StatusListExpansion {
    fun expand(bitstring: String): InputStream
}