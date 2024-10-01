package id.walt.policies.policies

import id.walt.credentials.Claims
import id.walt.credentials.JwtClaims
import id.walt.credentials.VcClaims
import id.walt.credentials.utils.VCFormat
import id.walt.policies.DatePolicyUtils.checkJwt
import id.walt.policies.DatePolicyUtils.checkVc
import id.walt.policies.DatePolicyUtils.policyUnavailable
import id.walt.policies.NotBeforePolicyException
import id.walt.policies.CredentialWrapperValidatorPolicy
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
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
class NotBeforeDatePolicy : CredentialWrapperValidatorPolicy(
) {

    override val name = "not-before"
    override val description =
        "Verifies that the credentials not-before date (for JWT: `nbf`, if unavailable: `iat` - 1 min) is correctly exceeded."
    override val supportedVCFormats = setOf(VCFormat.jwt_vc, VCFormat.jwt_vc_json, VCFormat.ldp_vc, VCFormat.sd_jwt_vc)

    companion object {
        private val vcClaims = listOf<Claims>(VcClaims.V2.NotBefore, VcClaims.V1.NotBefore)
        private val jwtClaims = listOf<Claims>(JwtClaims.NotBefore, JwtClaims.IssuedAt)
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> {
        val nbf = getIssuanceDateKeyValuePair(data) ?: return policyUnavailable
        val now = Clock.System.now()
        return if (isBeyondNow(nbf, now)) {
            buildFailureResult(now, nbf.second, nbf.first)
        } else {
            buildSuccessResult(now, nbf.second, nbf.first)
        }
    }

    private fun isBeyondNow(nbf: Pair<Claims, Instant>, now: Instant) = when (nbf.first) {
        JwtClaims.IssuedAt -> nbf.second.minus(1.minutes)
        else -> nbf.second
    }.let {
        it > now
    }

    private fun getIssuanceDateKeyValuePair(data: JsonObject): Pair<Claims, Instant>? =
        checkVc(data["vc"]?.jsonObject, vcClaims) ?: checkVc(data, vcClaims)
        ?: checkJwt(data, jwtClaims)

    private fun buildFailureResult(
        now: Instant,
        nbf: Instant,
        key: Claims,
    ): Result<Any> = (nbf - now).let {
        Result.failure(
            NotBeforePolicyException(
                date = nbf,
                dateSeconds = nbf.epochSeconds,
                availableIn = it,
                availableInSeconds = it.inWholeSeconds,
                key = key.getValue()
            )
        )
    }

    private fun buildSuccessResult(
        now: Instant,
        nbf: Instant,
        key: Claims,
    ) = (now - nbf).let {
        Result.success(
            JsonObject(
                mapOf(
                    "date" to JsonPrimitive(nbf.toString()),
                    "date_seconds" to JsonPrimitive(nbf.epochSeconds),
                    "available_since" to JsonPrimitive(it.toString()),
                    "available_since_seconds" to JsonPrimitive(it.inWholeSeconds),
                    "used_key" to JsonPrimitive(key.getValue()),
                    "policy_available" to JsonPrimitive(true)
                )
            )
        )
    }
}
