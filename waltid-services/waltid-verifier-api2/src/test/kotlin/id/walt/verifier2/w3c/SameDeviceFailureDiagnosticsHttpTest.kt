package id.walt.verifier2.w3c

import id.walt.policies2.vc.VCPolicyList
import id.walt.policies2.vc.policies.CredentialSignaturePolicy
import id.walt.policies2.vc.policies.ExpirationDatePolicy
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.events.Verifier2WebhookRecorder
import kotlin.test.Test

class SameDeviceFailureDiagnosticsHttpTest {
    @Test
    fun `same device policy failures expose diagnostics through info webhook and SSE`() {
        val policies = Verification2Session.DefinedVerificationPolicies(vc_policies = VCPolicyList(listOf(
            CredentialSignaturePolicy(), ExpirationDatePolicy(),
        )))
        Verifier2WebhookRecorder().start().use { webhook ->
            runW3cPolicyHttpCases(listOf(
                W3cPolicyHttpCase("same device failed policy diagnostics", false, policies, expirationOffset = -3600, sameDevice = true),
                W3cPolicyHttpCase("cross device failed policy diagnostics", false, policies, expirationOffset = -3600),
            ), webhook = webhook)
        }
    }
}
