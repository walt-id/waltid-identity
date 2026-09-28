package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.config.ConformanceSuiteAlias
import id.walt.openid4vp.conformance.testplans.plans.vci.wallet.VciWalletSdJwtDpop
import id.walt.openid4vp.conformance.testplans.plans.vp.wallet.Oid4vpWalletVariantPlan
import id.walt.openid4vp.conformance.testplans.plans.vp.wallet.WalletVariantMatrix
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ConformanceSuiteAliasTest {
    @Test
    fun concurrentRunsDoNotShareAStableAlias() {
        assertEquals("redirecturi-111", ConformanceSuiteAlias.unique("redirecturi", "111"))
        assertNotEquals(
            ConformanceSuiteAlias.unique("redirecturi", "111"),
            ConformanceSuiteAlias.unique("redirecturi", "222"),
        )
    }

    @Test
    fun walletPlanAliasesAreRunScoped() {
        val variant = WalletVariantMatrix.all().first()
        val plan = Oid4vpWalletVariantPlan(
            walletVariant = variant,
            walletApiUrl = "http://wallet.example/openid4vp/authorize",
            conformanceHost = "conformance.example",
            conformancePort = 443,
        )
        val alias = plan.configuration.getValue("alias").jsonPrimitive.content
        assertTrue(alias.startsWith("${variant.id}-"), alias)
        assertNotEquals(variant.id, alias)
    }

    @Test
    fun vciWalletPlanAliasesAreRunScoped() {
        val plan = VciWalletSdJwtDpop(
            walletApiUrl = "http://wallet.example",
            credentialOfferEndpoint = "http://wallet.example/credential-offer",
            redirectUri = "http://wallet.example/callback",
            conformanceHost = "conformance.example",
            conformancePort = 443,
        )
        val alias = plan.configuration.getValue("alias").jsonPrimitive.content
        assertTrue(alias.startsWith("vci_wallet_sdjwt_dpop-"), alias)
        assertNotEquals("vci_wallet_sdjwt_dpop", alias)
    }
}
