package id.walt.credentials.verification

import kotlinx.serialization.json.JsonObject

abstract class CredentialDataValidatorPolicy(
    override val name: String,
    override val description: String? = null,
    override val argumentTypes: List<VerificationPolicyArgumentType>? = null
) : VerificationPolicy(name, description, "credential-data-validator", argumentTypes) {

    abstract suspend fun verify(data: JsonObject, args: Any? = null, context: Map<String, Any>): Result<Any>

}
