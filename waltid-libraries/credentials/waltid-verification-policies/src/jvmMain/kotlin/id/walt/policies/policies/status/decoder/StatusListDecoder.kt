package id.walt.policies.policies.status.decoder

import java.io.InputStream

interface StatusListDecoder {
    fun decode(bitstring: String): InputStream
}