package id.walt.crypto2.hash

import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.security.MessageDigest

/**
 * JVM implementation of [Digest] backed by a thread-local JCA [MessageDigest].
 */
internal class JcaDigest(
    override val algorithm: HashAlgorithm,
) : Digest {

    private val messageDigest = ThreadLocal.withInitial {
        MessageDigest.getInstance(algorithm.toJcaName())
    }

    override fun update(input: ByteArray) = messageDigest.get().update(input)

    override fun update(input: ByteString) = update(input.toByteArray())

    override fun update(input: String) = update(input.toByteArray())

    override fun finish(): ByteArray = messageDigest.get().digest()

    override fun finishToByteString() = finish().toByteString()

    override suspend fun finishAsync() = finish()

    override suspend fun finishAsyncToByteString() = finishAsync().toByteString()

    override fun reset() = messageDigest.get().reset()
}
