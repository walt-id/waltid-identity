package id.walt.policies2.vc

import id.walt.policies2.vc.policies.CredentialVerificationPolicy2
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class CredentialPolicyResult(
    val policy: CredentialVerificationPolicy2,
    val success: Boolean,
    val result: JsonElement? = null,
    val error: String? = null
)

