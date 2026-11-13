package id.walt.openid4vci.preauthorized

import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

class InvalidPreAuthorizedCodeLifetimeException(maxLifetimeSeconds: Long) :
    IllegalArgumentException("Pre-authorized code lifetime must be between 1 and $maxLifetimeSeconds seconds")

/** Issuer-controlled settings for pre-authorized codes. */
@Serializable
data class PreAuthorizedCodeConfig(
    val defaultLifetimeSeconds: Long = 300,
    val maxLifetimeSeconds: Long = 300,
    val maxFailedTxCodeAttempts: Int = 5,
) {
    init {
        require(maxLifetimeSeconds > 0 && maxLifetimeSeconds.seconds.isFinite()) {
            "Pre-authorized code maximum lifetime must be positive and finite"
        }
        require(defaultLifetimeSeconds in 1..maxLifetimeSeconds) {
            "Pre-authorized code default lifetime must be positive and no greater than the maximum lifetime"
        }
        require(maxFailedTxCodeAttempts > 0) {
            "Pre-authorized code failed attempt limit must be positive"
        }
    }

    internal fun resolveLifetimeSeconds(requestedSeconds: Long?): Long {
        val lifetime = requestedSeconds ?: defaultLifetimeSeconds
        if (lifetime !in 1..maxLifetimeSeconds) {
            throw InvalidPreAuthorizedCodeLifetimeException(maxLifetimeSeconds)
        }
        return lifetime
    }
}
