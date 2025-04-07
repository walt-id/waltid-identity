package id.walt.policies.policies

import id.walt.w3c.Claims
import id.walt.w3c.JwtClaims
import id.walt.w3c.utils.VCFormat
import id.walt.policies.CredentialWrapperValidatorPolicy
import id.walt.policies.DatePolicyUtils.checkJwt
import id.walt.policies.DatePolicyUtils.checkVc
import id.walt.policies.DatePolicyUtils.policyUnavailable
import id.walt.policies.ExpirationDatePolicyException
import id.walt.w3c.VcClaims
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
class ExpirationDatePolicy : CredentialWrapperValidatorPolicy(
) {

    override val name = "expired"
    override val description = "Verifies that the credentials expiration date (`exp` for JWTs) has not been exceeded."
    override val supportedVCFormats = setOf(VCFormat.jwt_vc, VCFormat.jwt_vc_json, VCFormat.ldp_vc, VCFormat.sd_jwt_vc)

    companion object {
        private val vcClaims = listOf<Claims>(VcClaims.V2.NotAfter, VcClaims.V1.NotAfter)
        private val jwtClaims = listOf(JwtClaims.NotAfter)
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> {
        val (key, exp) = getExpirationKeyValuePair(data) ?: return policyUnavailable
        val now = Clock.System.now()

        return if (now > exp) {
            buildFailureResult(now, exp, key)
        } else {
            buildSuccessResult(now, exp, key)
        }
    }

    private fun getExpirationKeyValuePair(data: JsonObject): Pair<Claims, Instant>? =
        checkVc(data["vc"]?.jsonObject, vcClaims) ?: checkVc(data, vcClaims) ?: checkJwt(data, jwtClaims)

    private fun buildFailureResult(now: Instant, exp: Instant, key: Claims) = (now - exp).let {
        Result.failure<ExpirationDatePolicyException>(
            ExpirationDatePolicyException(
                date = exp,
                dateSeconds = exp.epochSeconds,
                expiredSince = it,
                expiredSinceSeconds = it.inWholeSeconds,
                key = key.getValue(),
                policyAvailable = true
            )
        )
    }

    private fun buildSuccessResult(now: Instant, exp: Instant, key: Claims) = (exp - now).let {
        Result.success(
            JsonObject(
                mapOf(
                    "date" to JsonPrimitive(exp.toString()),
                    "date_seconds" to JsonPrimitive(exp.epochSeconds),
                    "expires_in" to JsonPrimitive(it.toString()),
                    "expires_in_seconds" to JsonPrimitive(it.inWholeSeconds),
                    "used_key" to JsonPrimitive(key.getValue()),
                    "policy_available" to JsonPrimitive(true)
                )
            )
        )
    }
}
