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
    val testName: String = "unknown",
    val conformanceTestId: String,
    val conformanceStatus: String? = null,
    val conformanceResult: String? = null,
    val verifierStatus: Verification2Session.VerificationSessionStatus? = null,
    val walletStatus: String? = null,
    val errorMessage: String? = null
) {
    val passed: Boolean
        get() = conformanceResult == "PASSED" && 
                (verifierStatus == null || verifierStatus == Verification2Session.VerificationSessionStatus.SUCCESSFUL)
    
    val message: String?
        get() = errorMessage ?: when {
            conformanceResult != "PASSED" -> "Conformance: $conformanceResult"
            verifierStatus != null && verifierStatus != Verification2Session.VerificationSessionStatus.SUCCESSFUL -> 
                "Verifier: $verifierStatus"
            else -> null
        }
}
