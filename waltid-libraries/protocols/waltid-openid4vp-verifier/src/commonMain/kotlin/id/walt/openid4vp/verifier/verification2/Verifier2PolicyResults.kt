package id.walt.openid4vp.verifier.verification2

import id.walt.policies2.vc.CredentialPolicyResult
import id.walt.policies2.vp.policies.VPPolicy2
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Verifier2PolicyResults(
    @SerialName("vp_policies")
    val vpPolicies: Map<String, Map<String, VPPolicy2.PolicyRunResult>>,

    @SerialName("vc_policies")
    val vcPolicies: List<CredentialPolicyResult>,

    @SerialName("specific_vc_policies")
    val specificVcPolicies: Map<String, List<CredentialPolicyResult>>
) {

    val overallSuccess: Boolean =
        vpPolicies.all { it.value.all { it.value.success } } &&
        vcPolicies.all { it.success } &&
                specificVcPolicies.values.all { policies -> policies.all { it.success } }

}
