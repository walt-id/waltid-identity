package id.walt.credentials.verification

import id.walt.credentials.verification.policies.*
import id.walt.credentials.verification.policies.vp.HolderBindingPolicy
import id.walt.credentials.verification.policies.vp.MaximumCredentialsPolicy
import id.walt.credentials.verification.policies.vp.MinimumCredentialsPolicy
import kotlinx.serialization.Serializable

object PolicyManager {
    private val mappedPolicies = HashMap<String, VerificationPolicy>()

    // TODO: policies
    /*
     *  "presentation_definition",
     *  "revoked",
     *  "rego",
     *  multi signature?
     *
     * >>> NEXT:
            "challenge",
            "webhook"
     */

    @Serializable
    data class PolicyInformation(
        val description: String? = null,
        val policyType: String? = null,
        val argumentType: List<VerificationPolicy.VerificationPolicyArgumentType>? = null,
    )

    fun listPolicyDescriptions() = mappedPolicies.mapValues {
        PolicyInformation(
            description = it.value.description,
            policyType = it.value.policyType,
            argumentType = it.value.argumentTypes
        )
    }

    fun registerPolicies(vararg policies: VerificationPolicy) {
        policies.forEach { policy ->
            if (mappedPolicies.containsKey(policy.name))
                throw IllegalArgumentException("Policy does already exist: ${policy.name} (mapped to ${mappedPolicies[policy.name]!!::class.simpleName}). Choose another name for your policy (${policy::class.simpleName}.")

            mappedPolicies[policy.name] = policy
        }
    }

    init {
        registerPolicies(
            JwtSignaturePolicy(),
            JsonSchemaPolicy(),
            ExpirationDatePolicy(),
            NotBeforeDatePolicy(),
            WebhookPolicy(),
            MinimumCredentialsPolicy(),
            MaximumCredentialsPolicy(),
            HolderBindingPolicy(),
            AllowedIssuerPolicy()
        )
    }

    fun getPolicy(name: String): VerificationPolicy =
        mappedPolicies[name] ?: throw IllegalArgumentException("No policy found by name: $name")


}
