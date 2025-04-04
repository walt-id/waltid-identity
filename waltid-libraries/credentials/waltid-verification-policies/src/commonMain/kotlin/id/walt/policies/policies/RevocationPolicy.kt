package id.walt.policies.policies

import id.walt.w3c.utils.VCFormat
import id.walt.policies.CredentialWrapperValidatorPolicy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
abstract class RevocationPolicyMp : CredentialWrapperValidatorPolicy(
) {

    override val name = "revoked-status-list"
    override val description = "Verifies Credential Status"
    override val supportedVCFormats = setOf(VCFormat.jwt_vc, VCFormat.jwt_vc_json, VCFormat.ldp_vc)

    abstract override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any>
}

@Serializable
expect class RevocationPolicy() : RevocationPolicyMp {
    override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any>
}
