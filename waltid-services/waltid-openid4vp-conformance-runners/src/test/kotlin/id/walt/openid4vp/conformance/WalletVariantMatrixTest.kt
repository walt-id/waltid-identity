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
