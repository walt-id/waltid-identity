package id.walt.policies.policies.vp

import id.walt.policies.CredentialWrapperValidatorPolicy
import id.walt.w3c.utils.VCFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
class MaximumCredentialsPolicy : CredentialWrapperValidatorPolicy(
) {
    override val name = "maximum-credentials"
    override val description = "Verifies that a maximum number of credentials in the Verifiable Presentation is not exceeded"
    override val supportedVCFormats = setOf(VCFormat.jwt_vp, VCFormat.jwt_vp_json)

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> {
        val n = (args as JsonPrimitive).int

        val presentedCount = data["vp"]!!.jsonObject["verifiableCredential"]?.jsonArray?.count()
            ?: return Result.success(JsonObject(mapOf("policy_available" to JsonPrimitive(false))))

        val success = presentedCount <= n

        return if (success)
            Result.success(
                JsonObject(
                    mapOf(
                        "total" to JsonPrimitive(presentedCount),
                        "remaining" to JsonPrimitive(n - presentedCount)
                    )
                )
            )
        else {
            Result.failure(
              id.walt.policies.MaximumCredentialsException(
                total = presentedCount,
                exceeded = presentedCount - n
              )
            )
        }
    }
}
