@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package id.walt.crypto2.hash

internal interface HasherProvider {
    fun create(algorithm: HashAlgorithm): Hash
}

internal expect fun getPlatformDefaultHasherProvider(): HasherProvider

/**
 * Entry point for obtaining stateless hashers.
 *
 * Typical usage:
 * ```
 * val hasher = HashFactory.create(HashAlgorithm.SHA_256)
 * val hash = hasher.hash(message)
 * ```
 */
class HashFactory private constructor() {

    companion object {
        private val defaultProvider = getPlatformDefaultHasherProvider()

        /**
         * Creates a [Hash] instance bound to [algorithm].
         *
         * @param algorithm hash algorithm to instantiate.
         * @return a stateless hasher suitable for one-shot computations.
         */
        fun create(algorithm: HashAlgorithm): Hash =
            defaultProvider.create(algorithm)
    }
}
