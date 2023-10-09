package id.walt.didlib.verification.policies

import id.walt.didlib.verification.CredentialWrapperValidatorPolicy
import id.walt.didlib.verification.NotBeforePolicyException
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Duration.Companion.minutes

class NotBeforeDatePolicy : CredentialWrapperValidatorPolicy(
    "not-before",
    "Verifies that the credentials not-before date (for JWT: `nbf`, if unavailable: `iat` - 1 min) is correctly exceeded."
) {
    override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> {
        var successfulKey = ""
        fun getEpochTimestamp(key: String) =
            data[key]?.jsonPrimitive?.longOrNull?.let { Instant.fromEpochSeconds(it) }.also {
                successfulKey = key
            }

        val nbf = getEpochTimestamp("nbf") ?: getEpochTimestamp("iat")?.minus(1.minutes)
        ?: return Result.success(JsonObject(mapOf("policy_available" to JsonPrimitive(false))))

        val now = Clock.System.now()

        if (nbf > now) {
            val availableIn = nbf - now
            return Result.failure(
                NotBeforePolicyException(
                    date = nbf,
                    dateSeconds = nbf.epochSeconds,
                    availableIn = availableIn,
                    availableInSeconds = availableIn.inWholeSeconds,
                    key = successfulKey
                )
            )
        } else {
            check(successfulKey != "")

            val availableSince = now - nbf

            return Result.success(
                JsonObject(
                    mapOf(
                        "date" to JsonPrimitive(nbf.toString()),
                        "date_seconds" to JsonPrimitive(nbf.epochSeconds),
                        "available_since" to JsonPrimitive(availableSince.toString()),
                        "available_since_seconds" to JsonPrimitive(availableSince.inWholeSeconds),
                        "used_key" to JsonPrimitive(successfulKey),
                        "policy_available" to JsonPrimitive(true)
                    )
                )
            )
        }
    }
}
