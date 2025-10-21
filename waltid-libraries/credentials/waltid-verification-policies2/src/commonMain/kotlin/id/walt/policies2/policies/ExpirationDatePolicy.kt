@file:OptIn(ExperimentalTime::class)

package id.walt.policies2.policies

import com.nfeld.jsonpathkt.JsonPath
import id.walt.credentials.formats.DigitalCredential
import id.walt.policies2.ExpirationDatePolicyException
import id.walt.w3c.Claims
import id.walt.w3c.JwtClaims
import id.walt.w3c.VcClaims
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
@SerialName("expiration")
class ExpirationDatePolicy : VerificationPolicy2() {
    override val id = "expiration"

    companion object {
        private val vcClaims = listOf<Claims>(VcClaims.V2.NotAfter, VcClaims.V1.NotAfter)
        private val jwtClaims = listOf(JwtClaims.NotAfter)
        private val claims = (vcClaims union jwtClaims).map { JsonPath.compile(it.getValue()) }
    }

    @Serializable
    data class ExpirationDateClaimCheckResult(
        val date: Instant,

        @SerialName("date_seconds")
        val dateSeconds: Long,

        @SerialName("expires_in")
        val expiresIn: Duration,

        @SerialName("expires_in_seconds")
        val expiresInSeconds: Long,

        override val claim: String
    ) : PolicyClaimChecker.ClaimCheckResultSuccess()

    override suspend fun verify(credential: DigitalCredential): Result<JsonElement> {
        return PolicyClaimChecker.checkClaim(credential, claims) { claim ->
            require(this is JsonPrimitive) { "Claim at $claim is not a JSON primitive" }

            val storedDate = this.longOrNull?.let { Instant.fromEpochSeconds(this.long) } ?: Instant.parse(this.content)
            val now = Clock.System.now()

            val isExpired = now > storedDate

            if (isExpired) {
                val expiredSince = now - storedDate
                Result.failure(
                    ExpirationDatePolicyException(
                        date = storedDate,
                        dateSeconds = storedDate.epochSeconds,
                        expiredSince = expiredSince,
                        expiredSinceSeconds = expiredSince.inWholeSeconds,
                        key = claim.toString()
                    )
                )
            } else {
                val expiresIn = storedDate - now

                Result.success(
                    ExpirationDateClaimCheckResult(
                        date = storedDate,
                        dateSeconds = storedDate.epochSeconds,
                        expiresIn = expiresIn,
                        expiresInSeconds = expiresIn.inWholeSeconds,
                        claim = claim.toString()
                    ),
                )
            }
        }
    }
}
