package id.walt.verifier2.data

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

// Per-session first, then whatever the verifier is configured to retain for, then the historical
// default. A configured retention of Duration.INFINITE means keep indefinitely, which
// persistenceExpirationDate() expresses as null - no expiry date, so nothing ever discards it.
fun GeneralFlowConfig.effectiveRetentionDate(retention: Duration?, now: Instant = Clock.System.now()): Instant? = when {
    retentionDate != null -> retentionDate
    retention != null -> retention.takeIf { it.isFinite() }?.let { now.plus(it) }
    else -> now.plus(DEFAULT_RETENTION_YEARS, DateTimeUnit.YEAR, TimeZone.UTC)
}
