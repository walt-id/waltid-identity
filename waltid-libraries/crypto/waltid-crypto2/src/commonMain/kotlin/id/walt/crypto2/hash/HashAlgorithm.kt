package id.walt.crypto2.hash

import kotlinx.serialization.Serializable

@Serializable
enum class HashAlgorithm {
    SHA256,
    SHA384,
    SHA512,
    SHA3_256,
    SHA3_384,
    SHA3_512,
}

expect fun HashAlgorithm.createHasher(): Hasher
expect fun HashAlgorithm.createDigest(): Digest