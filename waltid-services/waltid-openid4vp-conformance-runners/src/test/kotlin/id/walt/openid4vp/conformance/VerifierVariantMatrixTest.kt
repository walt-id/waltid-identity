package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.testplans.plans.vp.verifier.VerifierVariant
import id.walt.openid4vp.conformance.testplans.plans.vp.verifier.VerifierVariantMatrix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Locks the verifier applicability rules transcribed from `AbstractVP1FinalVerifierTest`.
 *
 * Asking the suite for an inapplicable combination fails the whole plan creation with
 * "No test modules ... applicable", so these rules are worth asserting rather than rediscovering.
 */
class VerifierVariantMatrixTest {

    @Test
    fun `matrix covers every applicable combination`() {
        val all = VerifierVariantMatrix.all()

        // 2 formats x 3 prefixes x 2 request methods x 2 response modes = 24, less 4 excluded
        // redirect_uri + signed, 8 excluded x509 + url_query, 2 excluded mdoc + url_query and 1
        // excluded encrypted + url_query, plus 2 HAIP points.
        assertEquals(11, all.size)
        assertEquals(all.size, all.distinctBy { it.id }.size, "variant ids must be unique")
    }

    @Test
    fun `redirect_uri is driven for the one combination the suite can exercise`() {
        val redirectUri = VerifierVariantMatrix.all().filter { it.clientIdPrefix == "redirect_uri" }

        // redirect_uri implies url_query (signed requests are excluded), and the suite cannot drive
        // url_query with mdoc or with an encrypted response - both of its conditions there read the
        // request object that url_query never produces. So sd-jwt + direct_post is the whole set.
        assertEquals(1, redirectUri.size)
        assertEquals("sd_jwt_vc", redirectUri.single().credentialFormat)
        assertEquals("url_query", redirectUri.single().requestMethod)
        assertEquals("direct_post", redirectUri.single().responseMode)
    }

    @Test
    fun `url_query cannot be combined with anything needing a request object`() {
        // Both suite conditions involved read from `authorization_request_object`, which a url_query
        // request has none of. Suite limitations, not Verifier2 gaps.
        assertFalse(
            VerifierVariantMatrix.isApplicable(
                VerifierVariant("iso_mdl", "redirect_uri", "url_query", "direct_post", "plain_vp"),
            ),
            "mdoc SessionTranscript needs the request object",
        )
        assertFalse(
            VerifierVariantMatrix.isApplicable(
                VerifierVariant("sd_jwt_vc", "redirect_uri", "url_query", "direct_post.jwt", "plain_vp"),
            ),
            "response encryption reads client_metadata.jwks from the request object",
        )
    }

    @Test
    fun `redirect_uri cannot use a signed request`() {
        // OID4VP 1.0 §5.9.3-3.1.1: there is no key to authenticate the verifier with.
        assertFalse(
            VerifierVariantMatrix.isApplicable(
                VerifierVariant("sd_jwt_vc", "redirect_uri", "request_uri_signed", "direct_post", "plain_vp"),
            ),
        )
    }

    @Test
    fun `x509 prefixes cannot pass the request unsigned in the URL`() {
        // OID4VP 1.0 §5.9.3-3.5.1 and -3.6.1 both require a signed request object.
        listOf("x509_san_dns", "x509_hash").forEach { prefix ->
            assertFalse(
                VerifierVariantMatrix.isApplicable(
                    VerifierVariant("sd_jwt_vc", prefix, "url_query", "direct_post", "plain_vp"),
                ),
                "$prefix must not be applicable with url_query",
            )
        }
    }

    @Test
    fun `HAIP mandates an encrypted response and fixes three axes`() {
        val haip = VerifierVariantMatrix.haip()

        assertEquals(2, haip.size, "one HAIP point per credential format")
        assertTrue(haip.all { it.responseMode == "direct_post.jwt" })
        assertTrue(haip.all { it.clientIdPrefix == "x509_hash" && it.requestMethod == "request_uri_signed" })
        assertFalse(
            VerifierVariantMatrix.isApplicable(
                VerifierVariant("sd_jwt_vc", "x509_hash", "request_uri_signed", "direct_post", "haip"),
            ),
        )

        // The HAIP plan fixes prefix, request method and profile itself and rejects them being
        // restated, so only the two free axes may be sent.
        assertEquals(
            setOf("credential_format", "response_mode"),
            haip.first().testPlanCreationVariant().keys,
        )
    }

    @Test
    fun `plain VP sends all five axes`() {
        val plain = VerifierVariantMatrix.plainVp().first()

        assertEquals(
            setOf("credential_format", "response_mode", "client_id_prefix", "request_method", "vp_profile"),
            plain.testPlanCreationVariant().keys,
        )
    }
}
