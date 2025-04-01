package id.walt.policies.policies

import id.walt.credentials.utils.VCFormat
import id.walt.policies.CredentialWrapperValidatorPolicy
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject

@Serializable
abstract class CredentialStatusPolicyMp : CredentialWrapperValidatorPolicy(
) {

    override val name = "credential-status"
    override val description = "Verifies Credential Status"
    override val supportedVCFormats = setOf(VCFormat.jwt_vc, VCFormat.jwt_vc_json, VCFormat.ldp_vc, VCFormat.sd_jwt_vc)

    @Transient
    protected val logger = KotlinLogging.logger {}

    abstract override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any>
}

@Serializable
expect class CredentialStatusPolicy() : CredentialStatusPolicyMp {
    override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any>
}
