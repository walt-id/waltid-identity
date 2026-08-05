package id.walt.examples

import dev.whyoleg.cryptography.CryptographyProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest

/**
 * Runs every usage example on whichever platform the test is executed on and prints what worked, so the
 * consumer-level support matrix is visible next to the crypto-level one from
 * `SoftwareKeyAlgorithmMatrixTest`. A scenario that cannot run because the platform has no key for it is
 * reported as `NO_KEY` rather than failing - anything else failing is a bug.
 */
class UsageExampleMatrixTest {

    private enum class Outcome { OK, FAILED, NO_KEY }

    @Test
    fun `did and credential usage examples across key types`() = runTest(timeout = 10.minutes) {
        val rows = mutableListOf<Triple<String, String, Outcome>>()

        suspend fun scenario(label: String, keyLabel: String, hasKey: Boolean, block: suspend () -> Unit) {
            if (!hasKey) {
                rows += Triple(keyLabel, label, Outcome.NO_KEY)
                return
            }
            val outcome = try {
                block()
                Outcome.OK
            } catch (cause: Throwable) {
                println("  $keyLabel $label failed: ${cause::class.simpleName}: ${cause.message}")
                Outcome.FAILED
            }
            rows += Triple(keyLabel, label, outcome)
        }

        ExampleKeys.signingSpecs.forEach { (spec, algorithm) ->
            val keyLabel = ExampleKeys.label(spec)
            val hasKey = ExampleKeys.canSign(spec)

            DidUsageExamples.DidMethod.entries.forEach { method ->
                scenario("did:${method.method}", keyLabel, hasKey) {
                    val result = DidUsageExamples.createResolveAndVerify(method, ExampleKeys.signingKey(spec))
                    assertTrue(result.did.startsWith("did:${method.method}:"), "unexpected DID ${result.did}")
                    assertTrue(result.resolvedKeyCount > 0, "no verification key resolved from ${result.did}")
                    assertTrue(result.verifiedByResolvedKey, "resolved key did not verify a signature")
                }
            }

            scenario("W3C VC-JWT", keyLabel, hasKey) {
                val key = ExampleKeys.signingKey(spec)
                val result = CredentialUsageExamples.issueAndVerifyW3cVcJwt(key, algorithm)
                assertEquals(result.issuerDid, result.verifiedIssuer, "verified issuer does not match the DID")
            }

            scenario("SD-JWT VC", keyLabel, hasKey) {
                val key = ExampleKeys.signingKey(spec)
                val result = CredentialUsageExamples.issueAndVerifySdJwtVc(key, algorithm)
                assertTrue(result.signatureVerified, "issuer signature did not verify")
                assertTrue("given_name" in result.disclosedClaims, "disclosed claim is missing")
                assertTrue("birthdate" !in result.disclosedClaims, "withheld claim leaked into the presentation")
                assertTrue("birthdate" in result.issuedDisclosableClaims, "claim was never issued as disclosable")
            }
        }

        println(render(rows))

        val failedScenarios = rows.filter { it.third == Outcome.FAILED }.map { it.second }.toSet()
        assertEquals(
            knownGaps,
            failedScenarios,
            "Usage example results changed. Fixed scenarios must be removed from knownGaps, new failures are bugs.",
        )
    }

    /**
     * No known gaps: did:key material is decoded by crypto2 in common code now, so every platform resolves the
     * same way. An entry reappearing here means a platform-specific regression, and the assertion above names it.
     */
    private val knownGaps: Set<String> = emptySet()

    private fun render(rows: List<Triple<String, String, Outcome>>): String = buildString {
        appendLine()
        appendLine("Consumer usage examples - cryptography-kotlin provider: ${CryptographyProvider.Default.name}")
        val keyWidth = rows.maxOfOrNull { it.first.length } ?: 3
        val scenarioWidth = rows.maxOfOrNull { it.second.length } ?: 8
        rows.forEach { (key, scenario, outcome) ->
            appendLine("  ${key.padEnd(keyWidth)}  ${scenario.padEnd(scenarioWidth)}  $outcome")
        }
        val counts = rows.groupingBy { it.third }.eachCount()
        appendLine("  summary: " + Outcome.entries.joinToString { "$it=${counts[it] ?: 0}" })
    }
}
