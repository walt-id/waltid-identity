@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package id.walt.crypto2.hash

internal interface DigestProvider {
    fun create(algorithm: HashAlgorithm): Digest
}
internal expect fun getPlatformDefaultDigestProvider(): DigestProvider

/**
 * Entry point for obtaining stateful digest instances.
 *
 * Typical usage:
 * ```
 * val digest = DigestFactory.create(HashAlgorithm.SHA_256)
 * digest.update(part1)
 * digest.update(part2)
 * val result = digest.finish()
 * ```
 */
class DigestFactory private constructor() {

    companion object {
        private val defaultProvider = getPlatformDefaultDigestProvider()

        /**
         * Creates a new [Digest] bound to [algorithm].
         *
         * @param algorithm hash algorithm to instantiate.
         * @return a mutable digest that supports incremental updates.
         */
        fun create(algorithm: HashAlgorithm): Digest =
            defaultProvider.create(algorithm)
    }
}
