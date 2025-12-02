package id.walt.policies2.vc

import id.walt.policies2.vc.policies.CredentialVerificationPolicy2
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class PolicyResult(
    val policy: CredentialVerificationPolicy2,
    val success: Boolean,
    val result: JsonElement? = null,
    val error: String? = null
)

@Serializable
data class PolicyResults(
    //val vpPolicies: List<PolicyResult>, // TODO: vpPolicies
    val vcPolicies: List<PolicyResult>,
    val specificVcPolicies: Map<String, List<PolicyResult>>
) {

    val overallSuccess: Boolean =
        //vpPolicies.all { it.success } && // TODO: vpPolicies
        vcPolicies.all { it.success } &&
                specificVcPolicies.values.all { policies -> policies.all { it.success } }

}
