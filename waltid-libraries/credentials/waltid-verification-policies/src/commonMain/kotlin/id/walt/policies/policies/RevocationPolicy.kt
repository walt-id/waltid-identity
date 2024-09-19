package id.walt.policies.policies

import id.walt.policies.CredentialWrapperValidatorPolicy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
abstract class RevocationPolicyMp : CredentialWrapperValidatorPolicy(
) {

    override val name = "revoked_status_list"
    override val description = "Verifies Credential Status"

    abstract override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any>
}

@Serializable
expect class RevocationPolicy() : RevocationPolicyMp {
    override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any>
}
