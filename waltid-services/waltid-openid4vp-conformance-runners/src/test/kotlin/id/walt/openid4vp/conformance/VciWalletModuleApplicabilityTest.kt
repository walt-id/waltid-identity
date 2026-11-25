package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.testplans.plans.vci.wallet.VciWalletModuleApplicability
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VciWalletModuleApplicabilityTest {
    private val fapi = "fapi2-security-profile-final-client-test-happy-path"

    @Test
    fun suiteAcceptanceCannotHideAStorageFailureOrAnAcceptedInvalidCallback() {
        assertNotNull(VciWalletModuleApplicability.walletOutcomeFailure(fapi, "REJECTED"))
        assertNull(VciWalletModuleApplicability.walletOutcomeFailure(fapi, "STORED"))
        val invalidState = "fapi2-security-profile-final-client-test-ensure-authorization-response-with-invalid-state-fails"
        assertNull(VciWalletModuleApplicability.walletOutcomeFailure(invalidState, "REJECTED"))
        assertNotNull(VciWalletModuleApplicability.walletOutcomeFailure(invalidState, "STORED"))
        // The pinned suite declares missing expires_in a positive interoperability case.
        assertNull(VciWalletModuleApplicability.walletOutcomeFailure(
            "fapi2-security-profile-final-client-test-token-endpoint-response-without-expires_in", "STORED"))
    }

    @Test
    fun defaultCiDoesNotExpandIntoTheNewFapiDriver() {
        assertNotNull(VciWalletModuleApplicability.skipReason(fapi, fullPlan = false))
    }

    @Test
    fun fullPlanAndExplicitDriverChecksExecuteFapi() {
        assertNull(VciWalletModuleApplicability.skipReason(fapi, fullPlan = true))
        assertNull(VciWalletModuleApplicability.skipReason(fapi, fullPlan = false, explicitlySelected = true))
    }

    @Test
    fun deferredSelectionStaysOptInWhileImmediateCiIssuanceRuns() {
        val module = "oid4vci-1_0-wallet-test-credential-issuance"
        assertNotNull(VciWalletModuleApplicability.skipReason(module, fullPlan = false, deferred = true))
        assertNull(VciWalletModuleApplicability.skipReason(module, fullPlan = true, deferred = true))
        assertNull(VciWalletModuleApplicability.skipReason(module, fullPlan = false,
            explicitlySelected = true, deferred = true))
        assertNull(VciWalletModuleApplicability.skipReason(module, fullPlan = false))
    }

    @Test
    fun immediateIssuanceHasNoCapabilitySkip() {
        assertNull(VciWalletModuleApplicability.skipReason("oid4vci-1_0-wallet-test-credential-issuance", fullPlan = true))
        assertNull(VciWalletModuleApplicability.skipReason("oid4vci-1_0-wallet-test-credential-issuance", fullPlan = false))
    }
}
