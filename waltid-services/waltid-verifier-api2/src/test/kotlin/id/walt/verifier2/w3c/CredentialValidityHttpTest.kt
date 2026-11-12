package id.walt.verifier2.w3c

import id.walt.policies2.vc.VCPolicyList
import id.walt.policies2.vc.policies.CredentialSignaturePolicy
import id.walt.policies2.vc.policies.ExpirationDatePolicy
import id.walt.policies2.vc.policies.NotBeforePolicy
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.freePort
import kotlin.test.Test

class CredentialValidityHttpTest {
    @Test
    fun `configured date policies enforce outer JWT dates through HTTP presentations`() {
        fun policies(required: Boolean = false) = Verification2Session.DefinedVerificationPolicies(vc_policies = VCPolicyList(listOf(
            CredentialSignaturePolicy(), ExpirationDatePolicy(requireField = required), NotBeforePolicy(requireField = required),
        )))
        runW3cPolicyHttpCases(listOf(
            W3cPolicyHttpCase("valid outer dates", true, policies()),
            W3cPolicyHttpCase("expired outer exp", false, policies(), expirationOffset = -3600),
            W3cPolicyHttpCase("future outer nbf", false, policies(), notBeforeOffset = 3600),
            W3cPolicyHttpCase("outer dates satisfy required fields", true, policies(required = true)),
            W3cPolicyHttpCase("optional dates absent", true, policies(), notBeforeOffset = null, expirationOffset = null),
            W3cPolicyHttpCase("required dates absent", false, policies(required = true), notBeforeOffset = null, expirationOffset = null),
        ), port = freePort())
    }
}
