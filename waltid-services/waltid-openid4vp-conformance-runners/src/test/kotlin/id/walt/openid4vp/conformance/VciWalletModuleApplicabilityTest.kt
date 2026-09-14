package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.testplans.plans.vci.wallet.VciWalletModuleApplicability
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VciWalletModuleApplicabilityTest {

    @Test
    fun skipsFapi2ClientModules() {
        assertEquals(
            VciWalletModuleApplicability.FAPI2_SKIP_REASON,
            VciWalletModuleApplicability.skipReason(
                "fapi2-security-profile-final-client-test-happy-path",
                buildJsonObject { put("vci_credential_issuance_mode", "immediate") },
            ),
        )
    }

    @Test
    fun skipsDeferredIssuanceVariants() {
        assertEquals(
            VciWalletModuleApplicability.DEFERRED_SKIP_REASON,
            VciWalletModuleApplicability.skipReason(
                "oid4vci-1_0-wallet-test-credential-issuance",
                buildJsonObject { put("vci_credential_issuance_mode", "deferred") },
            ),
        )
    }

    @Test
    fun runsImmediateIssuanceModules() {
        assertNull(
            VciWalletModuleApplicability.skipReason(
                "oid4vci-1_0-wallet-test-client-attestation-challenge",
                buildJsonObject { put("vci_credential_issuance_mode", "immediate") },
            ),
        )
    }
}
