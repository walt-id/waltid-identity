package id.walt.openid4vp.conformance.testplans.plans

import id.walt.openid4vp.verifier.Verification2Session

data class TestPlanResult(
    val conformanceTestId: String,
    val conformanceStatus: String,
    val conformanceResult: String?,
    val verifierStatus: Verification2Session.VerificationSessionStatus,

    )
