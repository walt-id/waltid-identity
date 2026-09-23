package id.walt.openid4vp.conformance.testplans.plans.vp.verifier

import id.walt.policies2.vp.policies.VPPolicyList
import id.walt.policies2.vp.policies.VPVerificationPolicyManager
import id.walt.verifier2.data.Verification2Session

/**
 * VP policy configuration for OpenID4VP verifier conformance runs.
 *
 * Omits [mso_mdoc/issuer_auth] because the OIDF suite signs test mdocs with a
 * CA-flagged leaf certificate that fails ISO 18013-5 document-signer profile checks.
 * Possession / device-auth and other mdoc policies remain enabled.
 */
object ConformanceVerifierPolicies {
    private const val MSO_MDOC_ISSUER_AUTH = "mso_mdoc/issuer_auth"

    fun withoutMdocIssuerAuth(): Verification2Session.DefinedVerificationPolicies =
        Verification2Session.DefinedVerificationPolicies(
            vp_policies = VPPolicyList(
                jwtVcJson = VPVerificationPolicyManager.defaultJwtVcJsonPolicies,
                dcSdJwt = VPVerificationPolicyManager.defaultDcSdJwtPolicies,
                msoMdoc = VPVerificationPolicyManager.defaultMsoMdocPolicies.filterNot {
                    it.id == MSO_MDOC_ISSUER_AUTH
                },
            ),
        )
}
