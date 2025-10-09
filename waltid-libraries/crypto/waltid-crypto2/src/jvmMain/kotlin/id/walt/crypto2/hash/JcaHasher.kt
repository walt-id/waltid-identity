package id.walt.crypto2.hash

import java.security.MessageDigest

internal class JcaHasher(
    override val algorithm: HashAlgorithm,
) : Hasher {

    override fun hash(message: ByteArray): ByteArray {
        //not efficient, but safe for now
        val digest = MessageDigest.getInstance(resolveAlgorithmName(algorithm))
        return digest.digest(message)
    }
}