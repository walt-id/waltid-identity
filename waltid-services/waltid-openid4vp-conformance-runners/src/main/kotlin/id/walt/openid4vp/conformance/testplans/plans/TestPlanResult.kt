package id.walt.openid4vp.conformance.testplans.plans

import id.walt.openid4vp.conformance.testplans.runner.req.ExpectedModuleOutcome
import id.walt.verifier2.data.Verification2Session

/**
 * Test Plan Result
 *
 * Represents the outcome of a conformance test module execution.
 *
 * For verifier tests:
 * - conformanceResult: Result from conformance suite (wallet simulation)
 * - verifierStatus: Status from local verifier instance
 *
 * For wallet tests:
 * - conformanceResult: Result from conformance suite (verifier simulation)
 * - walletStatus: Status from local wallet instance
 */
data class TestPlanResult(
    val testName: String = "unknown",
    val conformanceTestId: String,
    val conformanceStatus: String? = null,
    val conformanceResult: String? = null,
    val verifierStatus: Verification2Session.VerificationSessionStatus? = null,
    val walletStatus: String? = null,
    val errorMessage: String? = null,
    /**
     * Why this module was not run at all, or `null` if it was.
     *
     * A skipped module is neither a pass nor a failure: it is recorded so that a module the suite
     * published but that cannot apply stays visible in the report instead of being silently dropped.
     */
    val skipReason: String? = null,
    /**
     * What this module was required to produce. When absent, the legacy rule applies
     * (suite result `PASSED` and, if observed, a successful verification session).
     */
    val expected: ExpectedModuleOutcome? = null,
) {
    val passed: Boolean
        get() = errorMessage == null && expected?.let {
            it.acceptsConformanceResult(conformanceResult) && it.acceptsVerifierStatus(verifierStatus)
        } ?: if (walletStatus != null) {
            // Wallet runners map TLS-only WARNING and screenshot REVIEW to walletStatus PASSED,
            // matching the verifier's ExpectedModuleOutcome.PASSED_OR_REVIEW so the GitHub
            // summary Error column stays empty the same way.
            walletStatus == "PASSED" &&
                (conformanceResult == null || conformanceResult in ACCEPTED_SUITE_RESULTS)
        } else {
            conformanceResult == "PASSED" &&
                (verifierStatus == null || verifierStatus == Verification2Session.VerificationSessionStatus.SUCCESSFUL)
        }

    val message: String?
        get() = errorMessage ?: when {
            passed -> null
            expected == null && conformanceResult != "PASSED" -> "Conformance: $conformanceResult"
            expected != null && !expected.acceptsConformanceResult(conformanceResult) ->
                "Conformance: $conformanceResult (expected one of ${expected.acceptedConformanceResults})"

            expected != null && !expected.acceptsVerifierStatus(verifierStatus) ->
                "Verifier: $verifierStatus (expected ${expected.verifier})"

            verifierStatus != null && verifierStatus != Verification2Session.VerificationSessionStatus.SUCCESSFUL ->
                "Verifier: $verifierStatus"

            else -> null
        }

    private companion object {
        val ACCEPTED_SUITE_RESULTS = setOf("PASSED", "WARNING", "REVIEW")
    }
}
