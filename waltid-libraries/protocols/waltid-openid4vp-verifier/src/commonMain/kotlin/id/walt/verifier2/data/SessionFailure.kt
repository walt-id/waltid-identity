@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.verifier2.data

import id.walt.policies2.vc.CredentialPolicyResult
import id.walt.policies2.vp.policies.VPPolicy2
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@JsonClassDiscriminator("type")
sealed class SessionFailure {
    abstract val reason: String

    @Serializable
    @SerialName("presentation_validation")
    data class PresentationValidation(
        override val reason: String,
        @SerialName("failed_policies")
        val failedPolicies: Map<String, Map<String, VPPolicy2.PolicyRunResult>>,
    ) : SessionFailure()

    @Serializable
    @SerialName("dcql_fulfillment")
    data class DcqlFulfillment(
        override val reason: String,
        val failure: DcqlFulfillmentFailure,
    ) : SessionFailure()

    @Serializable
    @SerialName("vc_policy_violations")
    data class VcPolicyViolations(
        override val reason: String,
        val violations: List<CredentialPolicyResult>,
    ) : SessionFailure()

    @Serializable
    @SerialName("wallet_error_response")
    data class WalletErrorResponse(
        override val reason: String,
        val error: String,
        @SerialName("error_description")
        val errorDescription: String? = null,
        val state: String? = null,
    ) : SessionFailure()
}

@Serializable
data class DcqlFulfillmentFailure(
    @SerialName("missing_query_ids")
    val missingQueryIds: List<String> = emptyList(),
    @SerialName("unsatisfied_sets")
    val unsatisfiedSets: List<UnsatisfiedSet> = emptyList(),
    @SerialName("successfully_validated_query_ids")
    val successfullyValidatedQueryIds: List<String> = emptyList(),
)

@Serializable
data class UnsatisfiedSet(
    val options: List<List<String>>,
)
