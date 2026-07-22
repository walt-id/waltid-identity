package id.walt.crypto2.algorithms

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class DigestAlgorithm(val name: String) {
    init {
        require(name.isNotBlank()) { "Digest algorithm name cannot be blank" }
    }

    companion object {
        val SHA_1 = DigestAlgorithm("SHA-1")
        val SHA_224 = DigestAlgorithm("SHA-224")
        val SHA_256 = DigestAlgorithm("SHA-256")
        val SHA_384 = DigestAlgorithm("SHA-384")
        val SHA_512 = DigestAlgorithm("SHA-512")

        val SHA3_224 = DigestAlgorithm("SHA3-224")
        val SHA3_256 = DigestAlgorithm("SHA3-256")
        val SHA3_384 = DigestAlgorithm("SHA3-384")
        val SHA3_512 = DigestAlgorithm("SHA3-512")

        val MD5 = DigestAlgorithm("MD5")
        val RIPEMD_160 = DigestAlgorithm("RIPEMD-160")

        val entries = listOf(
            SHA_1, SHA_224, SHA_256, SHA_384, SHA_512,
            SHA3_224, SHA3_256, SHA3_384, SHA3_512,
            MD5, RIPEMD_160,
        )
    }

}

val DigestAlgorithm.outputSizeBytes: Int?
    get() = when (this) {
        DigestAlgorithm.MD5 -> 16
        DigestAlgorithm.SHA_1, DigestAlgorithm.RIPEMD_160 -> 20
        DigestAlgorithm.SHA_224, DigestAlgorithm.SHA3_224 -> 28
        DigestAlgorithm.SHA_256, DigestAlgorithm.SHA3_256 -> 32
        DigestAlgorithm.SHA_384, DigestAlgorithm.SHA3_384 -> 48
        DigestAlgorithm.SHA_512, DigestAlgorithm.SHA3_512 -> 64
        else -> null
    }
