package id.walt.credentials.verification.policies

import id.walt.credentials.verification.CredentialWrapperValidatorPolicy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
abstract class RevocationPolicyMp : CredentialWrapperValidatorPolicy(
    "revoked_status_list", "Verifies Credential Status"
)  {
    abstract override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any>
}

@Serializable
expect class RevocationPolicy(): RevocationPolicyMp {
    override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any>
}
