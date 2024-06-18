package id.walt.credentials.verification.policies.vp

import id.walt.credentials.verification.CredentialWrapperValidatorPolicy
import id.walt.credentials.verification.MinimumCredentialsException
import kotlinx.serialization.json.*
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class MinimumCredentialsPolicy : CredentialWrapperValidatorPolicy(
    name = "minimum-credentials",
    description = "Verifies that a minimum number of credentials are included in the Verifiable Presentation"
) {
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun verify(data: JsonElement, args: Any?, context: Map<String, Any>): Result<Any> {
        val n = (args as JsonPrimitive).int
        val presentedCount = data.jsonObject["vp"]!!.jsonObject["verifiableCredential"]?.jsonArray?.count()
            ?: return Result.success(JsonObject(mapOf("policy_available" to JsonPrimitive(false))))

        val success = presentedCount >= n

        return if (success)
            Result.success(
                JsonObject(
                    mapOf(
                        "total" to JsonPrimitive(presentedCount),
                        "extra" to JsonPrimitive(presentedCount - n)
                    )
                )
            )
        else {
            Result.failure(
                MinimumCredentialsException(
                    total = presentedCount,
                    missing = n - presentedCount
                )
            )
        }
    }
}
