package id.walt.openid4vp.conformance.testplans.plans.vp.verifier

import id.walt.policies2.vc.CredentialPolicyResult
import id.walt.policies2.vp.policies.VPPolicyList
import id.walt.policies2.vp.policies.VPVerificationPolicyManager
import id.walt.verifier2.data.Verification2Session
import id.walt.policies2.vc.VCPolicyList
import id.walt.policies2.vc.policies.CredentialSignaturePolicy
import id.walt.policies2.vc.policies.StatusPolicy
import id.walt.policies2.vc.policies.status.model.IETFStatusPolicyAttribute

/**
 * VP policy configuration for OpenID4VP verifier conformance runs.
 *
 * Full-plan mode uses the pinned suite's current document-signer profile and all mdoc policies.
 * The older shared CI suite retains its existing issuer-auth compatibility configuration.
 */
object ConformanceVerifierPolicies {
    /** A signer/parsing failure is not proof that a revoked status value was evaluated. */
    internal fun revocationFailureReason(results: List<CredentialPolicyResult>): String? {
        val evaluatedRevocation = results.any {
            it.policy.id == "credential-status" && !it.success &&
                it.error?.contains("Status validation failed: expected one of [0], but got 1") == true
        }
        return if (evaluatedRevocation) null else
            "Revocation was not demonstrated: rejection did not evaluate revoked status value 1"
    }

    private const val MSO_MDOC_ISSUER_AUTH = "mso_mdoc/issuer_auth"

    fun unsupportedModuleReason(module: String): String? =
        if (module == "oid4vp-1final-verifier-present-credential-on-identifier-list") {
            "ISO identifier-list validation is not implemented by the configured status policy; " +
                "a generic unsupported-status rejection cannot establish identifier-list checking"
        } else null

    fun withoutMdocIssuerAuth(): Verification2Session.DefinedVerificationPolicies = configured(fullPlan = false)

    fun fullPlan(): Verification2Session.DefinedVerificationPolicies = configured(fullPlan = true)

    private fun configured(fullPlan: Boolean): Verification2Session.DefinedVerificationPolicies =
        Verification2Session.DefinedVerificationPolicies(
            // Explicit VC policies replace the verifier's signature-only default, so retain it.
            vc_policies = if (fullPlan) VCPolicyList(listOf(
                CredentialSignaturePolicy(),
                StatusPolicy(IETFStatusPolicyAttribute(value = 0u)),
            )) else null,
            vp_policies = VPPolicyList(
                jwtVcJson = VPVerificationPolicyManager.defaultJwtVcJsonPolicies,
                dcSdJwt = VPVerificationPolicyManager.defaultDcSdJwtPolicies,
                msoMdoc = VPVerificationPolicyManager.defaultMsoMdocPolicies.filterNot {
                    !fullPlan && it.id == MSO_MDOC_ISSUER_AUTH
                },
            ),
        )
}
