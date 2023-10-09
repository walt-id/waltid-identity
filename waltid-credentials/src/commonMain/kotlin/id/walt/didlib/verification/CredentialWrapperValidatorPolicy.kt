package id.walt.didlib.verification

import kotlinx.serialization.json.JsonObject

abstract class CredentialWrapperValidatorPolicy(
    override val name: String,
    override val description: String? = null
) : VerificationPolicy(name, description) {

    abstract suspend fun verify(data: JsonObject, args: Any? = null, context: Map<String, Any>): Result<Any>

}
