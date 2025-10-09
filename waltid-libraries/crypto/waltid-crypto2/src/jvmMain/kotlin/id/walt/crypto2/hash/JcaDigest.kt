package id.walt.crypto2.hash

import java.security.MessageDigest

internal class JcaDigest(
    override val algorithm: HashAlgorithm,
) : Digest {

    //not efficient, but safe for now
    private val messageDigest: MessageDigest =
        MessageDigest.getInstance(resolveAlgorithmName(algorithm))

    override fun update(
        input: ByteArray,
        offset: Int,
        length: Int,
    ) {
        messageDigest.update(input, offset, length)
    }

    override fun digest(): ByteArray = messageDigest.digest()

    override fun reset() {
        messageDigest.reset()
    }
}