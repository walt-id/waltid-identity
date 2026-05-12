package id.walt.verifier2.verification2

import id.walt.policies2.vc.CredentialPolicyResult
import id.walt.policies2.vp.policies.VPPolicy2
import id.walt.verifier2.data.AttributedCredentialPolicyResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Verifier2PolicyResults(
    @SerialName("vp_policies")
    val vpPolicies: Map<String, Map<String, VPPolicy2.PolicyRunResult>>,

    @SerialName("vc_policies")
    val vcPolicies: List<CredentialPolicyResult>,

    @SerialName("specific_vc_policies")
    val specificVcPolicies: Map<String, List<CredentialPolicyResult>>,

    /**
     * Per-credential attributed view of [vcPolicies]. Carries `queryId` + `credentialIndex` so a
     * vc_policy failure can be pinned to the specific credential that failed.
     * Null on sessions produced before this field existed.
     */
    @SerialName("attributed_vc_policies")
    val attributedVcPolicies: List<AttributedCredentialPolicyResult>? = null,

    /**
     * Per-credential attributed view of [specificVcPolicies]. Same shape rationale as
     * [attributedVcPolicies].
     */
    @SerialName("attributed_specific_vc_policies")
    val attributedSpecificVcPolicies: Map<String, List<AttributedCredentialPolicyResult>>? = null,
) {

    val overallSuccess: Boolean =
        vpPolicies.all { it.value.all { it.value.success } } &&
                vcPolicies.all { it.success } &&
                specificVcPolicies.values.all { policies -> policies.all { it.success } }

}
