@file:OptIn(ExperimentalTime::class)

package id.walt.policies

import id.walt.w3c.Claims
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

object DatePolicyUtils {

    val policyUnavailable = Result.success(JsonObject(mapOf("policy_available" to JsonPrimitive(false))))

    fun checkVc(data: JsonObject?, claims: List<Claims>) = check(data, claims) {
        instantConverter(it)
    }

    fun checkJwt(data: JsonObject?, claims: List<Claims>) = check(data, claims) {
        epochInstantConverter(it)
    }

    private fun check(data: JsonObject?, claims: List<Claims>, converter: (String) -> Instant) =
        claims.firstOrNull { claim ->
            !data?.jsonObject?.get(claim.getValue())?.jsonPrimitive?.content.isNullOrEmpty()
        }?.let {
            Pair(it, converter(data!!.jsonObject[it.getValue()]!!.jsonPrimitive.content))
        }

    private fun instantConverter(value: String) = Instant.parse(value)

    private fun epochInstantConverter(value: String) = value.toLongOrNull()?.let { Instant.fromEpochSeconds(it) }
        ?: throw IllegalArgumentException("Couldn't parse epoch seconds from: $value")
}
