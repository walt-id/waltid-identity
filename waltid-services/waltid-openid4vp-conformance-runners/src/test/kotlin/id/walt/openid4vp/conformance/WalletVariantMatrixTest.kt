package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.testplans.plans.vp.wallet.WalletVariant
import id.walt.openid4vp.conformance.testplans.plans.vp.wallet.WalletVariantMatrix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Locks the wallet applicability rules transcribed from `AbstractVP1FinalWalletTest`, plus the
 * Wallet2 capability limits. Asking the suite for an inapplicable combination fails plan creation
 * outright with "No test modules ... applicable".
 */
class WalletVariantMatrixTest {

    @Test
    fun `both wallet plans are driven`() {
        val planNames = WalletVariantMatrix.all().map { it.planName }.toSet()

        // The non-HAIP plan was previously never requested at all.
        assertEquals(
            setOf("oid4vp-1final-wallet-test-plan", "oid4vp-1final-wallet-haip-test-plan"),
            planNames,
        )
    }

    @Test
    fun `matrix shape`() {
        val all = WalletVariantMatrix.all()

        // redirect_uri x {url_query, request_uri_unsigned} x 2 response modes x 2 formats = 8
        // (x509_san_dns + x509_hash + decentralized_identifier) x request_uri_signed
        //   x 2 response modes x 2 formats = 12, plus 2 HAIP points.
        assertEquals(22, all.size)
        assertEquals(all.size, all.distinctBy { it.id }.size, "variant ids must be unique")
        assertEquals(20, WalletVariantMatrix.plainVp().size)
        assertEquals(2, WalletVariantMatrix.haip().size)
    }

    @Test
    fun `redirect_uri cannot authenticate a signed request`() {
        // It carries no key, so there is nothing to verify a request object against.
        assertFalse(
            WalletVariantMatrix.isApplicable(
                WalletVariant("sd_jwt_vc", "redirect_uri", "request_uri_signed", "direct_post", "plain_vp"),
            ),
        )
        assertTrue(
            WalletVariantMatrix.isApplicable(
                WalletVariant("sd_jwt_vc", "redirect_uri", "url_query", "direct_post", "plain_vp"),
            ),
        )
    }

    @Test
    fun `x509 prefixes require a signed request`() {
        // OID4VP 1.0 §5.9.3-3.5.1 / -3.6.1: the verifier is authenticated through the request
        // object's signature and x5c chain, which an unsigned request does not carry.
        listOf("x509_san_dns", "x509_hash").forEach { prefix ->
            listOf("url_query", "request_uri_unsigned").forEach { method ->
                assertFalse(
                    WalletVariantMatrix.isApplicable(
                        WalletVariant("sd_jwt_vc", prefix, method, "direct_post", "plain_vp"),
                    ),
                    "$prefix must not be applicable with $method",
                )
            }
        }
    }

    @Test
    fun `HAIP mandates an encrypted response`() {
        assertTrue(WalletVariantMatrix.haip().all { it.encryptedResponse })
        assertFalse(
            WalletVariantMatrix.isApplicable(
                WalletVariant("sd_jwt_vc", "x509_hash", "request_uri_signed", "direct_post", "haip"),
            ),
        )
    }

    @Test
    fun `only the HAIP plan omits the fixed axes`() {
        val haip = WalletVariantMatrix.haip().first()
        assertEquals(setOf("credential_format", "response_mode"), haip.testPlanCreationVariant().keys)

        val plain = WalletVariantMatrix.plainVp().first()
        assertEquals(
            setOf("credential_format", "response_mode", "client_id_prefix", "request_method", "vp_profile"),
            plain.testPlanCreationVariant().keys,
        )
    }
}
