package id.walt.mdoc.mso

import org.kotlincrypto.core.digest.Digest

/**
 * Supported digest algorithms, for signed items
 */
enum class DigestAlgorithm(val value: String) {
    SHA256("SHA-256"),
    SHA512("SHA-512");

    companion object {
        private val sha256Digest = org.kotlincrypto.hash.sha2.SHA256()
        private val sha512Digest = org.kotlincrypto.hash.sha2.SHA512()
    }

    fun getHasher(): Digest = when (this) {
        SHA256 -> sha256Digest
        SHA512 -> sha512Digest
    }
}
