package id.walt.openid4vci.preauthorized

import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class InvalidPreAuthorizedOfferLifetimeException(maxLifetimeSeconds: Long) :
    IllegalArgumentException("expiresInSeconds must be between 1 and $maxLifetimeSeconds for pre-authorized offers")

data class PreAuthorizedCodePolicy(
    val maxLifetimeSeconds: Long = DEFAULT_LIFETIME_SECONDS,
    val maxFailedTxCodeAttempts: Int = 5,
) {
    init {
        require(maxLifetimeSeconds > 0 && maxLifetimeSeconds.seconds.isFinite()) {
            "Pre-authorized code maximum lifetime must be positive and finite"
        }
        require(maxFailedTxCodeAttempts > 0) {
            "Pre-authorized code failed attempt limit must be positive"
        }
    }

    fun offerExpiresAt(expiresInSeconds: Long, now: Instant): Instant {
        if (expiresInSeconds !in 1..maxLifetimeSeconds) {
            throw InvalidPreAuthorizedOfferLifetimeException(maxLifetimeSeconds)
        }
        return now + expiresInSeconds.seconds
    }

    internal companion object {
        const val DEFAULT_LIFETIME_SECONDS = 300L
    }
}
