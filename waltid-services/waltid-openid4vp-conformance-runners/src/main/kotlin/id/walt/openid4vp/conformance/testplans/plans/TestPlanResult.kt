package id.walt.openid4vp.conformance.testplans.plans

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
    val conformanceTestId: String,
    val conformanceStatus: String? = null, // Deprecated - use conformanceResult
    val conformanceResult: String? = null,
    val verifierStatus: Verification2Session.VerificationSessionStatus? = null,
    val walletStatus: String? = null,
    val errorMessage: String? = null
)
