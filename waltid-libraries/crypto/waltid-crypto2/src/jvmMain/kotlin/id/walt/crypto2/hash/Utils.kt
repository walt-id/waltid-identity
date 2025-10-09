package id.walt.crypto2.hash

internal val jcaAlgorithmNames = mapOf(
    HashAlgorithm.SHA256 to "SHA-256",
    HashAlgorithm.SHA384 to "SHA-384",
    HashAlgorithm.SHA512 to "SHA-512",
    HashAlgorithm.SHA3_256 to "SHA3-256",
    HashAlgorithm.SHA3_384 to "SHA3-384",
    HashAlgorithm.SHA3_512 to "SHA3-512",
)

internal fun resolveAlgorithmName(algorithm: HashAlgorithm): String =
    jcaAlgorithmNames[algorithm] ?: error("Unsupported hash algorithm: $algorithm")