package id.walt.openid4vp.conformance.testplans.runner.req

import id.walt.verifier2.data.Verification2Session.VerificationSessionStatus

/**
 * What the conformance suite and our own verifier are expected to report for one test module.
 *
 * Derived from the [ExpectedVerifierOutcome] a test plan declares in
 * [TestPlanConfiguration.moduleOutcomes]. Both halves have to be checked, because neither alone is
 * conclusive:
 * - [acceptedConformanceResults]: what the conformance suite is allowed to report.
 * - [verifier]: whether our verifier had to accept or reject the presentation.
 *
 * Positive verifier modules cannot reach `PASSED` since conformance-suite release-v5.2.2: once the
 * verifier answers the `direct_post` with 2xx, the suite cannot observe whether the VP Token was
 * really verified (OpenID4VP permits deferred verification), so it asks for screenshot evidence and
 * finishes with result `REVIEW`. Negative modules still reach `PASSED`, because there the verifier's
 * 4xx is itself the pass criterion.
 * See `AbstractVP1FinalVerifierTest.handleAuthorizationEndpointRequest` in the conformance suite.
 *
 * That is exactly why [verifier] matters: `REVIEW` only means "a human should look at the
 * screenshot", so on its own it would also be reported for a verifier that happily accepted a
 * broken presentation. Asserting the verification session outcome closes that hole.
 */
data class ExpectedModuleOutcome(
    val acceptedConformanceResults: Set<String>,
    val verifier: ExpectedVerifierOutcome,
) {
    fun acceptsConformanceResult(result: String?): Boolean = result in acceptedConformanceResults

    /**
     * Whether [status] is the verification outcome this module requires.
     *
     * Uses [VerificationSessionStatus.successful] instead of listing statuses, so terminal statuses
     * added later are classified by the verifier library itself.
     */
    fun acceptsVerifierStatus(status: VerificationSessionStatus?): Boolean = when (verifier) {
        ExpectedVerifierOutcome.ACCEPT -> status?.successful == true
        ExpectedVerifierOutcome.REJECT -> status?.successful == false
        // The "or skip" variants tolerate an unsupported feature leaving the session untouched.
        ExpectedVerifierOutcome.ACCEPT_OR_SKIP -> status == null || status.successful != false
        ExpectedVerifierOutcome.REJECT_OR_SKIP -> status == null || status.successful != true
    }

    companion object {
        /** Suite results that are not a failure for a module ending in manual review. */
        private val PASSED_OR_REVIEW = setOf("PASSED", "REVIEW")

        /**
         * Expectation for a module whose verifier outcome is [outcome].
         *
         * Only a rejection lets the suite reach `PASSED` on its own; anything that the verifier is
         * allowed to accept ends in `REVIEW` once the screenshot evidence has been supplied.
         */
        fun forOutcome(outcome: ExpectedVerifierOutcome): ExpectedModuleOutcome = when (outcome) {
            ExpectedVerifierOutcome.REJECT -> ExpectedModuleOutcome(setOf("PASSED"), outcome)
            ExpectedVerifierOutcome.REJECT_OR_SKIP ->
                ExpectedModuleOutcome(PASSED_OR_REVIEW, outcome)
            ExpectedVerifierOutcome.ACCEPT, ExpectedVerifierOutcome.ACCEPT_OR_SKIP ->
                ExpectedModuleOutcome(PASSED_OR_REVIEW, outcome)
        }

        /**
         * Substrings the conformance suite uses to name modules that feed the implementation under
         * test something invalid.
         *
         * Only consulted for modules a test plan has not declared in
         * [TestPlanConfiguration.moduleOutcomes], so that modules added by a future suite release
         * are still judged sensibly instead of silently counting as a pass.
         */
        private val NEGATIVE_MODULE_MARKERS = listOf("negative-test", "invalid-", "-iat-in-")

        /** Fallback verifier outcome for a module the test plan does not declare. */
        fun undeclaredOutcomeFor(testModule: String): ExpectedVerifierOutcome =
            if (NEGATIVE_MODULE_MARKERS.any { it in testModule }) {
                ExpectedVerifierOutcome.REJECT
            } else {
                ExpectedVerifierOutcome.ACCEPT
            }
    }
}
