package id.walt.policies.policies

import id.walt.policies.CredentialWrapperValidatorPolicy
import id.walt.policies.policies.status.StatusPolicyImplementation.verifyWithAttributes
import id.walt.policies.policies.status.Values
import id.walt.policies.policies.status.model.W3CStatusPolicyAttribute
import id.walt.w3c.utils.VCFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport


@Serializable
@OptIn(ExperimentalJsExport::class)
@JsExport
class RevocationPolicy : CredentialWrapperValidatorPolicy() {

    override val name = "revoked-status-list"
    override val description = "Verifies Credential Status"
    override val supportedVCFormats = setOf(VCFormat.jwt_vc, VCFormat.jwt_vc_json, VCFormat.ldp_vc)

    @Transient
    private val attribute = W3CStatusPolicyAttribute(value = 0u, purpose = "revocation", type = Values.STATUS_LIST_2021)

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> =
        verifyWithAttributes(data, attribute)
}