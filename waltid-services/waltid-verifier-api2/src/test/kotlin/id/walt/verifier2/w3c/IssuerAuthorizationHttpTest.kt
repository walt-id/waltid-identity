package id.walt.verifier2.w3c

import id.walt.policies2.vc.VCPolicyList
import id.walt.policies2.vc.policies.AllowedIssuerPolicy
import id.walt.policies2.vc.policies.CredentialSignaturePolicy
import id.walt.verifier2.data.Verification2Session
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test

class IssuerAuthorizationHttpTest {
    @Test
    fun `HTTP presentations enforce outer issuer allowlists and default assertion authorization`() {
        fun allowedIssuer(issuer: String) = Verification2Session.DefinedVerificationPolicies(vc_policies = VCPolicyList(listOf(
            CredentialSignaturePolicy(), AllowedIssuerPolicy(JsonPrimitive(issuer)),
        )))
        runW3cPolicyHttpCases(listOf(
            W3cPolicyHttpCase("allowed outer issuer", true, allowedIssuer(HTTP_POLICY_ISSUER_DID)),
            W3cPolicyHttpCase("disallowed outer issuer", false, allowedIssuer("did:example:trusted")),
            W3cPolicyHttpCase("authentication-only key with default policies", false, signingKeyId = "authentication"),
        ))
    }
}
