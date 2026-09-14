package id.walt.crypto2.providers.cryptography

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyEncodingFormat
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.derivationsPerformed
import id.walt.crypto2.keys.resetValidationCacheForTesting
import id.walt.crypto2.providers.CryptoOperation
import id.walt.crypto2.providers.CryptoRequirement
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
 * This pins the caching that removed that cost, so a regression is visible.
 */
class SoftwareKeyRestoreCostTest {

    // The platform's own provider set, not a hand-picked provider: which providers exist and what they can do
    // differs per target, and a test that assumes one provider covers every capability fails on the targets
    // where it does not - see the guards below.
    private val providers = defaultSoftwareKeyProviders()
    private val runtime = CryptoRuntime(providers)

    @Test
    fun `restoring a stored EC private key derives its public key only once`() = runTest {
        val spec = KeySpec.Ec(EcCurve.P256)
        val usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)

        // Generating and importing are separate capabilities, and a platform can offer one without the other,
        // so both are checked before the behaviour is exercised. Skipping is right here: the caching this
        // asserts lives in common code, so any target that can import at all proves it.
        if (providers.none { it.supports(requirement(CryptoOperation.GENERATE_KEY, spec, usages)) }) {
            println("  skipped: this platform cannot generate a P-256 signing key")
            return@runTest
        }
        val stored = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(id = KeyId("restore-cost"), spec = spec, usages = usages),
        ).storedKey
        if (providers.none {
                it.supports(requirement(CryptoOperation.IMPORT_KEY, spec, usages, stored.material.encodingFormat))
            }
        ) {
            println("  skipped: this platform cannot import a stored P-256 signing key")
            return@runTest
        }

        // Restoring the same stored key repeatedly must not re-derive the public key: the consistency proof is
        // what made restore expensive, and reusing it is the whole point. Asserted as a count rather than a
        // duration - the first version of this test pinned a wall-clock figure measured on one machine and
        // failed on a slower CI runner, which said nothing about the behaviour it meant to protect. Measured as
        // a delta from the first restore rather than against a literal 1, so the claim stays exactly "repeats
        // are free" on any platform, however many derivations the first one needs.
        resetValidationCacheForTesting()
        runtime.restore(stored)
        val afterFirstRestore = derivationsPerformed
        val repeats = 49

        val elapsed = measureTime { repeat(repeats) { runtime.restore(stored) } }

        println("  restore of a stored P-256 private key: ${elapsed.inWholeMicroseconds / repeats}us per call")
        assertEquals(
            afterFirstRestore,
            derivationsPerformed,
            "restoring the same stored key another $repeats times derived its public key " +
                "${derivationsPerformed - afterFirstRestore} more times, so the validated material is not " +
                "being reused",
        )
    }

    private fun requirement(
        operation: CryptoOperation,
        spec: KeySpec,
        usages: Set<KeyUsage>,
        keyEncoding: KeyEncodingFormat = KeyEncodingFormat.JWK,
    ) = CryptoRequirement(operation = operation, spec = spec, usages = usages, keyEncoding = keyEncoding)
}
