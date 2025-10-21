package id.walt.crypto2.hash

import okio.ByteString
import okio.ByteString.Companion.toByteString

/**
 * Stateful hashing contract that supports incremental updates followed by a finishing step.
 *
 * Implementations must support reuse: after calling [finish], clients may call [reset] to clear the
 * internal state and feed additional messages.
 */
interface Digest {

    /**
     * Identifier of the underlying hash algorithm.
     */
    val algorithm: HashAlgorithm

    /**
     * Feeds [input] into the digest state.
     *
     * @param input next chunk to be processed.
     */
    fun update(input: ByteArray)

    /**
     * Convenience overload that feeds a [ByteString] chunk into the digest.
     *
     * @param input next chunk to be processed.
     */
    fun update(input: ByteString) = update(input.toByteArray())

    /**
     * Convenience overload that feeds the [input] string into the digest.
     *
     * @param input next chunk to be processed.
     */
    fun update(input: String) = update(input.encodeToByteArray())

    /**
     * Suspend-friendly variant of [update].
     *
     * @param input next chunk to be processed.
     */
    suspend fun updateAsync(input: ByteArray) = update(input)

    /**
     * Suspend-friendly variant of [update] for [ByteString] inputs.
     *
     * @param input next chunk to be processed.
     */
    suspend fun updateAsync(input: ByteString) = update(input)

    /**
     * Suspend-friendly variant of [update] for string inputs.
     *
     * @param input next chunk to be processed.
     */
    suspend fun updateAsync(input: String) = update(input)

    /**
     * Finalises the digest computation and returns the raw bytes. Implementations must reset their
     * internal state to accept new data after this call.
     */
    fun finish(): ByteArray

    /**
     * Finalises the digest computation and returns the result as a [ByteString].
     */
    fun finishToByteString() = finish().toByteString()

    /**
     * Suspend-friendly variant of [finish].
     */
    suspend fun finishAsync() = finish()

    /**
     * Suspend-friendly variant of [finishToByteString].
     */
    suspend fun finishAsyncToByteString() = finishAsync().toByteString()

    /**
     * Clears any intermediate state and prepares the digest instance for a new message.
     */
    fun reset()
}
