package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.testplans.plans.vci.issuer.IssuerVariant
import id.walt.openid4vp.conformance.testplans.plans.vci.issuer.IssuerVariantMatrix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IssuerVariantMatrixTest {

    @Test
    fun generatesOnlyBaseIssuerPlanVariants() {
        val variants = IssuerVariantMatrix.all()

        assertEquals(288, variants.size)
        assertTrue(variants.all { it.isDefinedByBaseIssuerPlan })
        assertTrue(variants.all { it.fapiProfile == "vci" })
        assertFalse(variants.any {
            it.grantType == "pre_authorization_code" &&
                it.authorizationCodeFlowVariant == "wallet_initiated"
        })
    }

    @Test
    fun variantJsonContainsOnlyActiveBaseIssuerPlanAxes() {
        val variant = IssuerVariantMatrix.all().first()
        val keys = variant.toJsonObject().keys

        assertEquals(
            setOf(
                "fapi_profile",
                "sender_constrain",
                "client_auth_type",
                "vci_authorization_code_flow_variant",
                "credential_format",
                "authorization_request_type",
                "fapi_request_method",
                "vci_grant_type",
                "vci_credential_encryption",
            ),
            keys,
        )
        assertFalse("openid" in keys)
        assertFalse("fapi_response_mode" in keys)
    }

    @Test
    fun descriptionUsesRealVariantValues() {
        val variant = IssuerVariant(
            fapiProfile = "vci",
            credentialFormat = "sd_jwt_vc",
            grantType = "pre_authorization_code",
            authorizationCodeFlowVariant = "issuer_initiated",
            clientAuthType = "client_attestation",
            senderConstrain = "dpop",
            authorizationRequestType = "simple",
            requestMethod = "unsigned",
            credentialEncryption = "plain",
        )

        assertEquals(
            "OID4VCI 1.0 Issuer - " +
                "fapi_profile=vci, " +
                "sender_constrain=dpop, " +
                "client_auth_type=client_attestation, " +
                "vci_authorization_code_flow_variant=issuer_initiated, " +
                "credential_format=sd_jwt_vc, " +
                "authorization_request_type=simple, " +
                "fapi_request_method=unsigned, " +
                "vci_grant_type=pre_authorization_code, " +
                "vci_credential_encryption=plain",
            variant.description,
        )
    }
}
