package id.walt.crypto2.hash

import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.security.MessageDigest

/**
 * JVM implementation of [Hash] backed by a single-use JCA [MessageDigest].
 */
internal class JcaHash(
    override val algorithm: HashAlgorithm,
) : Hash {

    private val messageDigest = ThreadLocal.withInitial {
        MessageDigest.getInstance(algorithm.toJcaName())
    }

    override fun hash(message: ByteArray): ByteArray = messageDigest.get().digest(message)

    override fun hashToByteString(message: ByteString) = hash(message.toByteArray()).toByteString()

    override fun hash(message: String) = hash(message.toByteArray())

    override fun hashToByteString(message: String) = hash(message).toByteString()

    override suspend fun hashAsync(message: ByteArray): ByteArray = hash(message)

    override suspend fun hashAsyncToByteString(message: ByteString) = hashToByteString(message)

    override suspend fun hashAsync(message: String): ByteArray = hash(message)

    override suspend fun hashAsyncToByteString(message: String) = hashToByteString(message)
}
