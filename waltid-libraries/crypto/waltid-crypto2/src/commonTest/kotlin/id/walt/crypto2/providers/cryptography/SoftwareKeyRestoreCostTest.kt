package id.walt.crypto2.providers.cryptography

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import id.walt.crypto2.keys.derivationsPerformed
import id.walt.crypto2.keys.resetValidationCacheForTesting
import kotlin.time.measureTime

/**
 * Restoring a stored software key is on the hot path of every service that signs.
 *
 * A local Verifier2 load test spent roughly 1.3 CPU-seconds per presentation, and every thread-dump
 * sample above the elliptic-curve arithmetic pointed at `deriveEcPublicJwk`, reached from
 * [CryptographySoftwareKeyProvider.restore] by way of `validatePrivatePublicConsistency`. That check
 * re-derives the public key from the private scalar on **every** restore, so a service that loads its
 * signing key repeatedly pays a scalar multiplication each time.
 *
 * This pins the cost so a regression is visible, and quantifies what a restore may cost.
 */
class SoftwareKeyRestoreCostTest {

    private val provider = CryptographySoftwareKeyProvider()
    private val runtime = CryptoRuntime(listOf(provider))

    @Test
    fun `restoring a stored EC private key derives its public key only once`() = runTest {
        val key = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("restore-cost"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            ),
        )
        val stored = key.storedKey

        // Restoring the same stored key repeatedly must derive the public key once: the consistency proof
        // is what made restore expensive, and caching it is the whole point. Asserted as a count rather than
        // a duration - the first version of this test pinned a wall-clock figure measured on one machine and
        // failed on a slower CI runner, which said nothing about the behaviour it meant to protect.
        resetValidationCacheForTesting()
        val iterations = 50

        val elapsed = measureTime { repeat(iterations) { runtime.restore(stored) } }

        println("  restore of a stored P-256 private key: ${elapsed.inWholeMicroseconds / iterations}us per call")
        assertEquals(
            1,
            derivationsPerformed,
            "restoring the same stored key $iterations times derived its public key $derivationsPerformed " +
                "times, so the validated material is not being reused",
        )
    }
}
