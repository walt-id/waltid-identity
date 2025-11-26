@file:OptIn(ExperimentalTime::class)

package id.walt.policies2.policies

import com.nfeld.jsonpathkt.JsonPath
import id.walt.credentials.formats.DigitalCredential
import id.walt.policies2.NotBeforePolicyException
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
@SerialName("not-before")
class NotBeforePolicy : VerificationPolicy2() {
    override val id = "not-before"

    companion object {
        private val vcClaims = listOf<Claims>(VcClaims.V2.NotBefore, VcClaims.V1.NotBefore).map { it.getValue() }
        private val jwtClaims = listOf<Claims>(JwtClaims.NotBefore, JwtClaims.IssuedAt).map { it.getValue() }

        private val claims = (vcClaims union jwtClaims).map { JsonPath.compile(it) }
    }

    @Serializable
    data class NotBeforePolicyClaimCheckResult(
        val date: Instant,

        @SerialName("date_seconds")
        val dateSeconds: Long,

        @SerialName("available_since")
        val availableSince: Duration,

        @SerialName("available_since_seconds")
        val availableSinceSeconds: Long,

        override val claim: String
    ) : PolicyClaimChecker.ClaimCheckResultSuccess()

    override suspend fun verify(credential: DigitalCredential): Result<JsonElement> {
        return PolicyClaimChecker.checkClaim(credential, claims) { claim ->
            require(this is JsonPrimitive) { "Claim at $claim is not a JSON primitive" }

            val storedDate = this.longOrNull?.let { Instant.fromEpochSeconds(this.long) } ?: Instant.parse(this.content)
            val now = Clock.System.now()

            val isTooEarly = storedDate > now

            if (isTooEarly) {
                val availableIn = storedDate - now
                Result.failure(
                    NotBeforePolicyException(
                        date = storedDate,
                        dateSeconds = storedDate.epochSeconds,
                        availableIn = availableIn,
                        availableInSeconds = availableIn.inWholeSeconds,
                        key = claim.toString()
                    )
                )
            } else {
                val availableSince = now - storedDate

                Result.success(
                    NotBeforePolicyClaimCheckResult(
                        date = storedDate,
                        dateSeconds = storedDate.epochSeconds,
                        availableSince = availableSince,
                        availableSinceSeconds = availableSince.inWholeSeconds,
                        claim = claim.toString()
                    )
                )
            }
        }
    }
}
