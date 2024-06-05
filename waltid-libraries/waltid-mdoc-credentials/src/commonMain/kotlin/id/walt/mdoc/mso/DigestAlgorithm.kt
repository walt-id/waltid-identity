package id.walt.mdoc.mso

/**
 * Supported digest algorithms, for signed items
 */
enum class DigestAlgorithm(val value: String) {
  SHA256("SHA-256"),
  SHA512("SHA-512");

  fun getHasher() = when(this) {
    SHA256 -> korlibs.crypto.SHA256
    SHA512 -> korlibs.crypto.SHA512
  }
}