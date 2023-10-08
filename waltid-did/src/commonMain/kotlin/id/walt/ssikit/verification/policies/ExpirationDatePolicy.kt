package id.walt.ssikit.verification.policies

import id.walt.ssikit.verification.CredentialWrapperValidatorPolicy
import id.walt.ssikit.verification.ExpirationDatePolicyException
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class ExpirationDatePolicy : CredentialWrapperValidatorPolicy(
    "expired", "Verifies that the credentials expiration date (`exp` for JWTs) has not been exceeded."
) {
    override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> {
        val successfulKey = "exp"

        val exp = data["exp"]?.jsonPrimitive?.longOrNull?.let { Instant.fromEpochSeconds(it) }
            ?: return Result.success(
                JsonObject(mapOf("policy_available" to JsonPrimitive(false)))
            )

        val now = Clock.System.now()

        if (now > exp) {
            val expiredSince = now - exp
            return Result.failure(
                ExpirationDatePolicyException(
                    date = exp,
                    dateSeconds = exp.epochSeconds,
                    expiredSince = expiredSince,
                    expiredSinceSeconds = expiredSince.inWholeSeconds,
                    key = successfulKey,
                    policyAvailable = true
                )
            )
        } else {
            val expiresIn = exp - now

            return Result.success(
                JsonObject(
                    mapOf(
                        "date" to JsonPrimitive(exp.toString()),
                        "date_seconds" to JsonPrimitive(exp.epochSeconds),
                        "expires_in" to JsonPrimitive(expiresIn.toString()),
                        "expires_in_seconds" to JsonPrimitive(expiresIn.inWholeSeconds),
                        "used_key" to JsonPrimitive(successfulKey),
                        "policy_available" to JsonPrimitive(true)
                    )
                )
            )
        }
    }
}
