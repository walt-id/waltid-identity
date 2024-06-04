package id.walt.credentials.verification.policies

import id.walt.credentials.verification.CredentialWrapperValidatorPolicy
import kotlinx.serialization.json.*

abstract class RevocationPolicyMp : CredentialWrapperValidatorPolicy(
    "revoked_status_list", "Verifies Credential Status"
)  {
    abstract override suspend fun verify(data: JsonElement, args: Any?, context: Map<String, Any>): Result<Any>
}

expect class RevocationPolicy constructor(): RevocationPolicyMp
