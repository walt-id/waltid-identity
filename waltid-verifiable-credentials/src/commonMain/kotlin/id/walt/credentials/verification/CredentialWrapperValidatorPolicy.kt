package id.walt.credentials.verification

import kotlinx.serialization.json.JsonElement

abstract class CredentialWrapperValidatorPolicy(
    override val name: String,
    override val description: String? = null,
    override val argumentTypes: List<VerificationPolicyArgumentType>? = null
) : VerificationPolicy(name, description, "credential-wrapper-validator", argumentTypes) {

    abstract suspend fun verify(data: JsonElement, args: Any? = null, context: Map<String, Any>): Result<Any>

}
