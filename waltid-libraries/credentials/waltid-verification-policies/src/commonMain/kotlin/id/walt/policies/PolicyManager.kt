package id.walt.policies

import id.walt.policies.policies.*
import id.walt.policies.policies.vp.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
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

    fun listPolicyDescriptions() = mappedPolicies.mapValues { it.value.description }

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
            SdJwtVCSignaturePolicy(),
            JsonSchemaPolicy(),
            ExpirationDatePolicy(),
            NotBeforeDatePolicy(),
            WebhookPolicy(),
            MinimumCredentialsPolicy(),
            MaximumCredentialsPolicy(),
            HolderBindingPolicy(),
            AllowedIssuerPolicy(),
            RevocationPolicy(),
            PresentationDefinitionPolicy(),
            DynamicPolicy(),
            StatusPolicy(),
            VpRequiredCredentialsPolicy(),
        )
    }

    fun getPolicy(name: String): VerificationPolicy =
        mappedPolicies[name] ?: throw IllegalArgumentException("No policy found by name: $name")


}
