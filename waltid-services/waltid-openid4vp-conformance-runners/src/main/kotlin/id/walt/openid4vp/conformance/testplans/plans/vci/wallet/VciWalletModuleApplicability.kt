package id.walt.openid4vp.conformance.testplans.plans.vci.wallet

/** Preserve the shared CI profile while full-plan and explicit driver checks exercise FAPI. */
object VciWalletModuleApplicability {
    fun skipReason(module: String, fullPlan: Boolean, explicitlySelected: Boolean = false): String? =
        if (!fullPlan && !explicitlySelected && module.startsWith("fapi2-security-profile-final-client-test-")) {
            "FAPI client modules are outside the default CI profile; run full-plan mode or the explicit SDK driver check"
        } else null

    private val rejectingModules = setOf(
        "fapi2-security-profile-final-client-test-discovery-issuer-mismatch",
        "fapi2-security-profile-final-client-test-invalid-authorization-response-iss",
        "fapi2-security-profile-final-client-test-remove-authorization-response-iss",
        "fapi2-security-profile-final-client-test-ensure-authorization-response-with-invalid-state-fails",
        "fapi2-security-profile-final-client-test-ensure-authorization-response-with-invalid-missing-state-fails",
    )

    /** Suite acceptance alone does not establish successful storage or the expected SDK rejection. */
    fun walletOutcomeFailure(module: String, observed: String): String? {
        val expected = if (module in rejectingModules) "REJECTED" else "STORED"
        return if (observed == expected) null else "Expected wallet outcome $expected, observed $observed"
    }
}
