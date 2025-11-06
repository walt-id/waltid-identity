package id.walt.policies2

import id.walt.policies2.policies.VerificationPolicy2
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class PolicyResult(
    val policy: VerificationPolicy2,
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
