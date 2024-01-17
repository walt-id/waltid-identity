package id.walt.credentials.verification

import kotlinx.serialization.json.JsonElement

abstract class CredentialWrapperValidatorPolicy(
    override val name: String,
    override val description: String? = null
) : VerificationPolicy(name, description) {

    abstract suspend fun verify(data: JsonElement, args: Any? = null, context: Map<String, Any>): Result<Any>

}
