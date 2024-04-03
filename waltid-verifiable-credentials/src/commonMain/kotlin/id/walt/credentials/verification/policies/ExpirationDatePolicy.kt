package id.walt.credentials.verification.policies

import id.walt.credentials.verification.CredentialWrapperValidatorPolicy
import id.walt.credentials.verification.ExpirationDatePolicyException
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class ExpirationDatePolicy : CredentialWrapperValidatorPolicy(
    "expired", "Verifies that the credentials expiration date (`exp` for JWTs) has not been exceeded."
) {

    private fun JsonPrimitive.parseDate(): Instant? =
        longOrNull?.let { Instant.fromEpochSeconds(it) }
            ?: if (isString) Instant.parse(content) else null

    private fun JsonObject.retrieveExpirationDate(): Pair<Instant, String>? {
        return JSON_WRAPPER_KEYS.firstNotNullOfOrNull { get(it)?.jsonPrimitive?.parseDate()?.to(it) }
            ?: get("vc")?.jsonObject?.run {
                JSON_CREDENTIAL_KEYS.firstNotNullOfOrNull { get(it)?.jsonPrimitive?.parseDate()?.to(it) }
            }
    }


    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun verify(data: JsonElement, args: Any?, context: Map<String, Any>): Result<Any> {
        if (data !is JsonObject) return UNAVAILABLE_RESULT

        val (exp, successfulKey) = data.retrieveExpirationDate() ?: return UNAVAILABLE_RESULT
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

    companion object {
        private val UNAVAILABLE_RESULT = Result.success(JsonObject(mapOf("policy_available" to JsonPrimitive(false))))

        private val JSON_WRAPPER_KEYS = listOf("exp", "validUntil", "expirationDate")
        private val JSON_CREDENTIAL_KEYS = listOf("validUntil", "expirationDate")
    }
}
