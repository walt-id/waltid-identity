package id.walt.credentials.verification.policies.vp

import id.walt.credentials.verification.CredentialWrapperValidatorPolicy
import id.walt.credentials.verification.MaximumCredentialsException
import kotlinx.serialization.json.*

class MaximumCredentialsPolicy : CredentialWrapperValidatorPolicy(
    name = "maximum-credentials",
    description = "Verifies that a maximum number of credentials in the Verifiable Presentation is not exceeded"
) {
    override suspend fun verify(data: JsonElement, args: Any?, context: Map<String, Any>): Result<Any> {
        val n = (args as JsonPrimitive).int

        when (data) {
            is JsonObject -> data["vp"]!!.jsonObject["verifiableCredential"]?.jsonArray
            is JsonArray -> data
            else -> throw IllegalArgumentException("Can")
        }

        val presentedCount = data.jsonObject["vp"]!!.jsonObject["verifiableCredential"]?.jsonArray?.count()
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
                MaximumCredentialsException(
                    total = presentedCount,
                    exceeded = presentedCount - n
                )
            )
        }
    }
}
