package id.walt.policies.policies

import id.walt.policies.CredentialWrapperValidatorPolicy
import id.walt.policies.policies.status.StatusPolicyImplementation.verifyWithAttributes
import id.walt.policies.policies.status.content.JsonElementParser
import id.walt.policies.policies.status.model.StatusPolicyArgument
import id.walt.w3c.utils.VCFormat
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport


@Serializable
@OptIn(ExperimentalJsExport::class)
@JsExport
class StatusPolicy : CredentialWrapperValidatorPolicy() {

    override val name = "credential-status"
    override val description = "Verifies Credential Status"
    override val supportedVCFormats = setOf(VCFormat.jwt_vc, VCFormat.jwt_vc_json, VCFormat.ldp_vc, VCFormat.sd_jwt_vc)

    @Transient
    @Contextual
    private val argumentParser = JsonElementParser(serializer<StatusPolicyArgument>())

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> {
        requireNotNull(args) { "args required" }
        require(args is JsonElement) { "args must be JsonElement" }
        val arguments = argumentParser.parse(args)
        return verifyWithAttributes(data, arguments)
    }
}