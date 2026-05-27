package id.walt.policies2.vc.policies

import com.nfeld.jsonpathkt.JsonPath
import id.walt.credentials.formats.DigitalCredential
import id.walt.policies2.vc.ExpirationDatePolicyException
import id.walt.w3c.Claims
import id.walt.w3c.JwtClaims
import id.walt.w3c.VcClaims
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

@Serializable
@SerialName("expiration")
class ExpirationDatePolicy(
    /**
     * If true, the policy fails when the exp claim is absent entirely.
     * Default false for general use; set true for strict profiles (e.g. ETSI TS 119 472-1 EAA-5.2.7.1-03).
     */
    val requireField: Boolean = false
) : CredentialVerificationPolicy2() {
    override val id = "expiration"

    companion object {
        private val vcClaims = listOf<Claims>(VcClaims.V2.NotAfter, VcClaims.V1.NotAfter)
        private val jwtClaims = listOf(JwtClaims.NotAfter)
        private val claims = (vcClaims union jwtClaims).map { JsonPath.compile(it.getValue()) }
    }

    @Serializable
    @SerialName("ExpirationDateClaimCheckResult")
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

    override suspend fun verify(
        credential: DigitalCredential,
        context: PolicyExecutionContext
    ): Result<JsonElement> {
        return PolicyClaimChecker.checkClaim(credential, claims, requireField) { claim ->
            require(this is JsonPrimitive) { "Claim at $claim is not a JSON primitive" }

            val rawLong = this.longOrNull
            // RFC 7519 §4.1.4: NumericDate is seconds since epoch.
            // Values >= 1e12 (year ~33658) indicate milliseconds were used instead — non-conformant.
            require(rawLong == null || rawLong < 1_000_000_000_000L) {
                "Claim $claim value $rawLong appears to be in milliseconds, not seconds as required by RFC 7519 §4.1.4 (NumericDate)"
            }

            val storedDate = rawLong?.let { Instant.fromEpochSeconds(it) } ?: Instant.parse(this.content)
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
