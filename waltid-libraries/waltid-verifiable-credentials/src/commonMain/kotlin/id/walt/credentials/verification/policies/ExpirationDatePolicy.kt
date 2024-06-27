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
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun verify(data: JsonElement, args: Any?, context: Map<String, Any>): Result<Any> {
        val (key, exp) = getExpirationKeyValuePair(data) ?: return buildPolicyUnavailableResult()

        val now = Clock.System.now()

        return if (now > exp) {
            buildFailureResult(now, exp, key)
        } else {
            buildSuccessResult(now, exp, key)
        }
    }

    private fun getExpirationKeyValuePair(data: JsonElement): Pair<String, Instant>? =
        checkVc(data.jsonObject["vc"]) ?: checkJwt(data)

    private fun checkJwt(data: JsonElement?) =
        data?.jsonObject?.get("exp")?.jsonPrimitive?.longOrNull?.let { Pair("jwt:exp", Instant.fromEpochSeconds(it)) }

    private fun checkVc(data: JsonElement?) =
        data?.jsonObject?.get("validUntil")?.jsonPrimitive?.let {
            Pair("validUntil", Instant.parse(it.content))
        } ?: data?.jsonObject?.get("expirationDate")?.jsonPrimitive?.let {
            Pair("expirationDate", Instant.parse(it.content))
        }

    private fun buildPolicyUnavailableResult() = Result.success(
        JsonObject(mapOf("policy_available" to JsonPrimitive(false)))
    )

    private fun buildFailureResult(now: Instant, exp: Instant, key: String) = (now - exp).let {
        Result.failure<ExpirationDatePolicyException>(
            ExpirationDatePolicyException(
                date = exp,
                dateSeconds = exp.epochSeconds,
                expiredSince = it,
                expiredSinceSeconds = it.inWholeSeconds,
                key = key,
                policyAvailable = true
            )
        )
    }

    private fun buildSuccessResult(now: Instant, exp: Instant, key: String) = (exp - now).let {
        Result.success(
            JsonObject(
                mapOf(
                    "date" to JsonPrimitive(exp.toString()),
                    "date_seconds" to JsonPrimitive(exp.epochSeconds),
                    "expires_in" to JsonPrimitive(it.toString()),
                    "expires_in_seconds" to JsonPrimitive(it.inWholeSeconds),
                    "used_key" to JsonPrimitive(key),
                    "policy_available" to JsonPrimitive(true)
                )
            )
        )
    }
}
