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
        // redirect_uri + signed and 8 excluded x509 + url_query, plus 2 HAIP points.
        // The mdoc/encrypted + url_query exclusions were dropped when conformance-suite 5.2.3 moved
        // those conditions onto the effective authorization request; see VerifierVariantMatrix.
        assertEquals(14, all.size)
        assertEquals(all.size, all.distinctBy { it.id }.size, "variant ids must be unique")
    }

    @Test
    fun `redirect_uri is driven for every url_query combination`() {
        val redirectUri = VerifierVariantMatrix.all().filter { it.clientIdPrefix == "redirect_uri" }

        // redirect_uri implies url_query, since a signed request has no key to authenticate with.
        // Both formats and both response modes are drivable as of conformance-suite 5.2.3.
        assertEquals(4, redirectUri.size)
        assertTrue(redirectUri.all { it.requestMethod == "url_query" })
        assertEquals(setOf("sd_jwt_vc", "iso_mdl"), redirectUri.map { it.credentialFormat }.toSet())
        assertEquals(setOf("direct_post", "direct_post.jwt"), redirectUri.map { it.responseMode }.toSet())
    }

    @Test
    fun `url_query is drivable with mdoc and with an encrypted response since suite 5_2_3`() {
        // Until conformance-suite 5.2.3 both conditions read `authorization_request_object`, which a
        // url_query request never produces, so these combinations errored out before Verifier2 was
        // exercised. They now read the effective authorization request. This guard fails if a suite
        // downgrade reintroduces the old behaviour.
        assertTrue(
            VerifierVariantMatrix.isApplicable(
                VerifierVariant("iso_mdl", "redirect_uri", "url_query", "direct_post", "plain_vp"),
            ),
            "mdoc SessionTranscript now reads the effective authorization request",
        )
        assertTrue(
            VerifierVariantMatrix.isApplicable(
                VerifierVariant("sd_jwt_vc", "redirect_uri", "url_query", "direct_post.jwt", "plain_vp"),
            ),
            "response encryption now reads client_metadata.jwks from the effective request",
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
