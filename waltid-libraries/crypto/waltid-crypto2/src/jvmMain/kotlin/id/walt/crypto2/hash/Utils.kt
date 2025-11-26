package id.walt.crypto2.hash

internal fun HashAlgorithm.toJcaName() = when (this) {
    HashAlgorithm.SHA_256 -> "SHA-256"
    HashAlgorithm.SHA_384 -> "SHA-384"
    HashAlgorithm.SHA_512 -> "SHA-512"
    HashAlgorithm.SHA3_256 -> "SHA3-256"
    HashAlgorithm.SHA3_384 -> "SHA3-384"
    HashAlgorithm.SHA3_512 -> "SHA3-512"
    else -> error("Unsupported hash algorithm ${this.name}")
}