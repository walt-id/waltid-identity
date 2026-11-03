package id.walt.walletdemo.compose.logic

import kotlin.math.abs
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.char
import kotlinx.datetime.format.optional

/** Display policy only. Protocol values and validation always retain the original instant. */
object WalletDateTime {
    // Only complete, offset-bearing timestamps are instants. Date-only, floating, unsupported
    // precision and leap-second values stay verbatim instead of acquiring or losing information.
    // Use the library's field parsers and calendar validation, with RFC 3339 offset syntax.
    // Its general ISO preset additionally accepts offset hours alone and offset seconds.
    private val timestamp = DateTimeComponents.Format {
        date(LocalDate.Formats.ISO)
        char('T')
        hour(); char(':'); minute(); char(':'); second()
        optional { char('.'); secondFraction(1, 9) }
        optional("Z") { offsetHours(); char(':'); offsetMinutesOfHour() }
    }

    fun parseTimestamp(value: String): Instant? = timestamp.parseOrNull(value.trim().uppercase())?.let {
        runCatching {
            require(it.year in 1..9999)
            it.toInstantUsingOffset()
        }.getOrNull()
            ?.takeIf { instant -> instant.epochSeconds in -62_135_596_800L..253_402_300_799L }
    }

    /** Null settings mean the current device settings; an override must come from the use case. */
    fun format(instant: Instant, timeZoneId: String? = null, localeTag: String? = null, use24HourClock: Boolean? = null): WalletDateTimeText =
        platformFormatWalletInstant(instant, timeZoneId, localeTag, use24HourClock)
}

/** Independently localized fields, never reconstructed by splitting a localized sentence. */
data class WalletDateTimeText(val date: String, val time: String, val timeZone: String) {
    val supportingText: String get() = "$time ($timeZone)"
    val fullText: String get() = "$date, $supportingText"
}

internal expect fun platformFormatWalletInstant(instant: Instant, timeZoneId: String?, localeTag: String?, use24HourClock: Boolean?): WalletDateTimeText

internal fun timeZoneLabel(city: String?, specificName: String): String =
    if (city.isNullOrBlank() || city == specificName) specificName else "$city, $specificName"

/** CLDR's exemplar-city fallback for a geographical IANA ID; fixed offsets have no city. */
internal fun timeZoneCity(zoneId: String): String? =
    if (zoneId.substringBefore('/') in setOf("Africa", "America", "Antarctica", "Arctic", "Asia", "Atlantic", "Australia", "Europe", "Indian", "Pacific"))
        zoneId.substringAfterLast('/').replace('_', ' ') else null

internal fun utcOffsetLabel(totalSeconds: Int): String {
    if (totalSeconds == 0) return "UTC"
    val seconds = abs(totalSeconds)
    fun Int.padded() = toString().padStart(2, '0')
    return "UTC${if (totalSeconds < 0) "-" else "+"}${(seconds / 3600).padded()}:${(seconds / 60 % 60).padded()}" +
        if (seconds % 60 == 0) "" else ":${(seconds % 60).padded()}"
}

/** Insert exact subsecond digits after the seconds field, without editing quoted CLDR literals. */
internal fun withSecondFraction(pattern: String, fraction: String): String {
    if (fraction.isEmpty()) return pattern
    var quoted = false
    var index = 0
    while (index < pattern.length) {
        if (pattern[index] == '\'') quoted = !quoted
        if (!quoted && pattern[index] == 's') {
            while (index < pattern.length && pattern[index] == 's') index++
            return pattern.substring(0, index) + "'$fraction'" + pattern.substring(index)
        }
        index++
    }
    return pattern
}
