package id.walt.ktorauthnz.attempts

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Limits on failed authentication attempts.
 *
 * - Per session: a session that saw [maxFailuresPerSession] failed steps (e.g. wrong TOTP codes) fails for good.
 * - Per account identifier: after [maxFailuresPerIdentifier] failures within [identifierWindow] (e.g. wrong passwords
 *   for one username, across sessions) that identifier is locked until the window ends.
 *
 * A limit of 0 turns it off.
 */
data class AttemptLimits(
    val maxFailuresPerSession: Int = 5,
    val sessionWindow: Duration = 1.hours,
    val maxFailuresPerIdentifier: Int = 10,
    val identifierWindow: Duration = 15.minutes,
) {
    companion object {
        val DISABLED = AttemptLimits(maxFailuresPerSession = 0, maxFailuresPerIdentifier = 0)
    }
}
