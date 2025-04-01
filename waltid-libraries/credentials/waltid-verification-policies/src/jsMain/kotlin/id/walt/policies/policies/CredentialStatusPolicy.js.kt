package id.walt.policies.policies

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
actual class CredentialStatusPolicy : CredentialStatusPolicyMp() {
    actual override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> {
        TODO("Not yet implemented")
    }
}
