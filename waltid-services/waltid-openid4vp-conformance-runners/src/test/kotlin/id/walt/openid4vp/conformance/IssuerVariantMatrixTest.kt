package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.testplans.plans.vci.issuer.IssuerVariant
import id.walt.openid4vp.conformance.testplans.plans.vci.issuer.IssuerVariantMatrix
import id.walt.openid4vp.conformance.testplans.plans.vci.issuer.IssuerVariantSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IssuerVariantMatrixTest {

    @Test
    fun generatesOnlyBaseIssuerPlanVariants() {
        val variants = IssuerVariantMatrix.base()

        assertEquals(288, variants.size)
        assertTrue(variants.all { it.isDefinedByBaseIssuerPlan })
        assertTrue(variants.all { it.fapiProfile == "vci" })
        assertFalse(variants.any {
            it.grantType == "pre_authorization_code" &&
                it.authorizationCodeFlowVariant == "wallet_initiated"
        })
    }

    @Test
    fun addsHaipVariantsToTheCombinedMatrix() {
        val variants = IssuerVariantMatrix.all()

        assertEquals(296, variants.size)
        assertEquals(8, variants.count { it.isHaip })
        assertTrue(variants.filter { it.isHaip }.all { it.fapiProfile == "vci_haip" })
    }

    @Test
    fun variantJsonContainsOnlyActiveBaseIssuerPlanAxes() {
        val variant = IssuerVariantMatrix.base().first()
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

    @Test
    fun selectsTheTwelveBasicCiVariants() {
        val selected = IssuerVariantSelection(
            fapiProfiles = setOf("vci"),
            credentialFormats = setOf("sd_jwt_vc", "mdoc"),
            grantTypes = setOf("authorization_code", "pre_authorization_code"),
            authorizationCodeFlowVariants = setOf("issuer_initiated", "wallet_initiated"),
            clientAuthTypes = setOf("client_attestation"),
            senderConstrains = setOf("dpop"),
            authorizationRequestTypes = setOf("simple"),
            requestMethods = setOf("unsigned"),
            credentialEncryptions = setOf("plain", "encrypted"),
        ).select(IssuerVariantMatrix.base())

        assertEquals(12, selected.size)
        assertTrue(selected.all { it.fapiProfile == "vci" })
        assertFalse(selected.any {
            it.grantType == "pre_authorization_code" &&
                it.authorizationCodeFlowVariant == "wallet_initiated"
        })
    }
}
