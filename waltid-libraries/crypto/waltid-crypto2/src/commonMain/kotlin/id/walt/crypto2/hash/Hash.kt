package id.walt.crypto2.hash

import okio.ByteString
import okio.ByteString.Companion.toByteString

/**
 * Stateless hashing contract supporting synchronous and suspend-friendly helpers.
 *
 * Implementations must guarantee that repeated invocations with the same input should return an
 * identical result.
 */
interface Hash {

    /**
     * Identifier of the underlying hash algorithm.
     */
    val algorithm: HashAlgorithm

    /**
     * Calculates the digest of the provided [message] and returns the raw bytes.
     *
     * @param message payload to hash.
     */
    fun hash(message: ByteArray): ByteArray

    /**
     * Calculates the digest of the provided [message] and returns the result as a [ByteString].
     *
     * @param message payload to hash.
     */
    fun hashToByteString(message: ByteArray) = hash(message).toByteString()

    /**
     * Calculates the digest of the provided [message] and returns the raw bytes.
     *
     * @param message payload to hash.
     */
    fun hash(message: ByteString) = hash(message.toByteArray())

    /**
     * Calculates the digest of the provided [message] and returns the result as a [ByteString].
     *
     * @param message payload to hash.
     */
    fun hashToByteString(message: ByteString) = hash(message.toByteArray()).toByteString()

    /**
     * Calculates the digest of the UTF-8 encoded [message] string and returns the raw bytes.
     *
     * @param message string payload to hash.
     */
    fun hash(message: String) = hash(message.toByteArray())

    /**
     * Calculates the digest of the [message] string and returns the result as a [ByteString].
     *
     * @param message string payload to hash.
     */
    fun hashToByteString(message: String) = hash(message).toByteString()

    /**
     * Suspend-friendly variant of [hash].
     *
     * @param message payload to hash.
     */
    suspend fun hashAsync(message: ByteArray) = hash(message)

    /**
     * Suspend-friendly variant of [hashToByteString] for [ByteString] inputs.
     *
     * @param message payload to hash.
     */
    suspend fun hashAsyncToByteString(message: ByteString) = hashAsync(message.toByteArray()).toByteString()

    /**
     * Suspend-friendly variant of [hash] that accepts string payloads.
     *
     * @param message string payload to hash.
     */
    suspend fun hashAsync(message: String) = hashAsync(message.toByteArray())

    /**
     * Suspend-friendly variant of [hashToByteString] for string payloads.
     *
     * @param message string payload to hash.
     */
    suspend fun hashAsyncToByteString(message: String) = hashAsync(message).toByteString()
}
