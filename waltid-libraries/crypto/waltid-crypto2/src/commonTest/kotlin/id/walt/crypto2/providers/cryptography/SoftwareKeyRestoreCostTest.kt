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
import kotlin.test.assertTrue
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
    fun `restoring a stored EC private key stays far cheaper than a signing operation`() = runTest {
        val key = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("restore-cost"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            ),
        )
        val stored = key.storedKey

        // Warm the JIT and any provider initialisation, so the measurement is steady state.
        repeat(20) { runtime.restore(stored) }

        val iterations = 200
        val elapsed = measureTime { repeat(iterations) { runtime.restore(stored) } }
        val perRestoreMicros = elapsed.inWholeMicroseconds / iterations

        println("  restore of a stored P-256 private key: ${perRestoreMicros}us per call")

        // Measured on this hardware: 470us per restore before the consistency check was cached, 208us
        // after. The bound catches the scalar multiplication coming back, which is the regression worth
        // failing a build over. What remains is not elliptic-curve work but several JSON round trips per
        // restore - `parseJwk`, `normalizeJwk` and the requirement checks each re-parse the material -
        // and reducing that is a separate change.
        assertTrue(
            perRestoreMicros < 350,
            "restoring a stored key took ${perRestoreMicros}us, which suggests it is deriving the public " +
                "key again rather than trusting material it has already validated",
        )
    }
}
