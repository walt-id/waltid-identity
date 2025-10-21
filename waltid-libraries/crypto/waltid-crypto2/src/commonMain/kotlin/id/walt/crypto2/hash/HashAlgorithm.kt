package id.walt.crypto2.hash

@JvmInline
value class HashAlgorithm private constructor(val name: String) {

    companion object {
        val SHA_256 = HashAlgorithm("SHA256")
        val SHA_384 = HashAlgorithm("SHA384")
        val SHA_512 = HashAlgorithm("SHA512")

        val SHA3_256 = HashAlgorithm("SHA3-256")
        val SHA3_384 = HashAlgorithm("SHA3-384")
        val SHA3_512 = HashAlgorithm("SHA3-512")

        val entries = listOf(
            SHA_256, SHA_384, SHA_512,
            SHA3_256, SHA3_384, SHA3_512,
        )
    }

}
