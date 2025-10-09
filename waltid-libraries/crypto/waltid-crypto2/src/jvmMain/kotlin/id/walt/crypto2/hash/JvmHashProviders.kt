package id.walt.crypto2.hash

import java.security.MessageDigest

private val jcaAlgorithmNames = mapOf(
    HashAlgorithm.SHA256 to "SHA-256",
    HashAlgorithm.SHA384 to "SHA-384",
    HashAlgorithm.SHA512 to "SHA-512",
    HashAlgorithm.SHA3_256 to "SHA3-256",
    HashAlgorithm.SHA3_384 to "SHA3-384",
    HashAlgorithm.SHA3_512 to "SHA3-512",
)

private fun resolveAlgorithmName(algorithm: HashAlgorithm): String =
    jcaAlgorithmNames[algorithm] ?: error("Unsupported hash algorithm: $algorithm")

private class JcaHasher(
    override val algorithm: HashAlgorithm,
) : Hasher {

    override fun hash(message: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance(resolveAlgorithmName(algorithm))
        return digest.digest(message)
    }
}

object HasherFactory {

    fun create(algorithm: HashAlgorithm): Hasher = when (algorithm) {
        HashAlgorithm.SHA256 -> SHA256()
        HashAlgorithm.SHA384 -> SHA384()
        HashAlgorithm.SHA512 -> SHA512()
        HashAlgorithm.SHA3_256 -> SHA3_256()
        HashAlgorithm.SHA3_384 -> SHA3_384()
        HashAlgorithm.SHA3_512 -> SHA3_512()
    }

    private fun SHA256(): Hasher = JcaHasher(HashAlgorithm.SHA256)

    private fun SHA384(): Hasher = JcaHasher(HashAlgorithm.SHA384)

    private fun SHA512(): Hasher = JcaHasher(HashAlgorithm.SHA512)

    private fun SHA3_256(): Hasher = JcaHasher(HashAlgorithm.SHA3_256)

    private fun SHA3_384(): Hasher = JcaHasher(HashAlgorithm.SHA3_384)

    private fun SHA3_512(): Hasher = JcaHasher(HashAlgorithm.SHA3_512)
}

private class JcaDigest(
    override val algorithm: HashAlgorithm,
) : Digest {
    private val messageDigest: MessageDigest = MessageDigest.getInstance(resolveAlgorithmName(algorithm))

    override fun update(input: ByteArray, offset: Int, length: Int) {
        if (length == 0) {
            return
        }
        require(offset >= 0 && length >= 0 && offset + length <= input.size) {
            "Invalid offset/length: offset=$offset length=$length size=${input.size}"
        }
        messageDigest.update(input, offset, length)
    }

    override fun digest(): ByteArray = messageDigest.digest()

    override fun reset() {
        messageDigest.reset()
    }
}

object DigestFactory {

    fun create(algorithm: HashAlgorithm): Digest = when (algorithm) {
        HashAlgorithm.SHA256 -> SHA256()
        HashAlgorithm.SHA384 -> SHA384()
        HashAlgorithm.SHA512 -> SHA512()
        HashAlgorithm.SHA3_256 -> SHA3_256()
        HashAlgorithm.SHA3_384 -> SHA3_384()
        HashAlgorithm.SHA3_512 -> SHA3_512()
    }

    private fun SHA256(): Digest = JcaDigest(HashAlgorithm.SHA256)

    private fun SHA384(): Digest = JcaDigest(HashAlgorithm.SHA384)

    private fun SHA512(): Digest = JcaDigest(HashAlgorithm.SHA512)

    private fun SHA3_256(): Digest = JcaDigest(HashAlgorithm.SHA3_256)

    private fun SHA3_384(): Digest = JcaDigest(HashAlgorithm.SHA3_384)

    private fun SHA3_512(): Digest = JcaDigest(HashAlgorithm.SHA3_512)
}
