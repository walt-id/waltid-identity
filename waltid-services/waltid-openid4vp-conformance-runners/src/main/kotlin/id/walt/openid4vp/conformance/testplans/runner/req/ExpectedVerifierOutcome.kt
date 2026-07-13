package id.walt.openid4vp.conformance.testplans.runner.req

/**
 * Expected outcome for verifier conformance tests.
 * 
 * Used to map conformance test module IDs to their expected verification results,
 * allowing automated validation of verifier behavior in positive and negative test cases.
 */
enum class ExpectedVerifierOutcome {
    /**
     * Verifier should accept the presentation (valid credential, valid proof)
     */
    ACCEPT,
    
    /**
     * Verifier should reject the presentation (invalid signature, expired, etc.)
     */
    REJECT,
    
    /**
     * Test may be skipped if the verifier doesn't support the feature,
     * otherwise should accept
     */
    ACCEPT_OR_SKIP,
    
    /**
     * Test may be skipped if the verifier doesn't support the feature,
     * otherwise should reject
     */
    REJECT_OR_SKIP
}
