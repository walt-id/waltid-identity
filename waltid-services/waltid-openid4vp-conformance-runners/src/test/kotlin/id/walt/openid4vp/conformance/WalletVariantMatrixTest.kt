package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.testplans.plans.vci.wallet.WalletVariantMatrix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WalletVariantMatrixTest {

    @Test
    fun generatesOnlyValidBasicWalletPlanContexts() {
        val variants = WalletVariantMatrix.basic()

        assertEquals(1_728, variants.size)
        assertTrue(variants.all { it.fapiProfile == "vci" })
        assertFalse(variants.any {
            it.authorizationCodeFlowVariant == "wallet_initiated" &&
                (it.grantType != "authorization_code" || it.credentialOfferVariant != null)
        })
        assertTrue(variants.filter { it.authorizationCodeFlowVariant != "wallet_initiated" }
            .all { it.credentialOfferVariant in setOf("by_value", "by_reference") })
    }

    @Test
    fun addsTheSuiteDefinedHaipPlanContexts() {
        val variants = WalletVariantMatrix.all()
        val haipVariants = variants.filter { it.isHaip }

        assertEquals(1_734, variants.size)
        assertEquals(6, haipVariants.size)
        assertTrue(haipVariants.all {
            it.grantType == "authorization_code" &&
                it.clientAuthType == "client_attestation" &&
                it.senderConstrain == "dpop" &&
                it.authorizationRequestType == "simple" &&
                it.requestMethod == "unsigned" &&
                it.credentialEncryption == "suite_matrix" &&
                it.credentialIssuanceMode == "suite_matrix"
        })
    }
}
