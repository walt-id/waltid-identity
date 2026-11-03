@file:OptIn(ExperimentalForeignApi::class)

package id.walt.walletdemo.compose.logic

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.toNSDate
import kotlinx.datetime.toNSTimeZone
import kotlin.time.Instant

internal actual fun platformFormatWalletInstant(instant: Instant, timeZoneId: String?, localeTag: String?, use24HourClock: Boolean?): WalletDateTimeText {
    val locale = localeTag?.let { NSLocale(it) } ?: NSLocale.currentLocale
    val zone = timeZoneId?.let(TimeZone::of) ?: TimeZone.currentSystemDefault()
    val local = instant.toLocalDateTime(zone)
    val date = Instant.fromEpochSeconds(instant.epochSeconds).toNSDate()
    val number = NSNumberFormatter().apply { this.locale = locale; minimumIntegerDigits = 9u; usesGroupingSeparator = false }
    val zero = NSNumberFormatter().apply { this.locale = locale }.stringFromNumber(NSNumber(int = 0)) ?: "0"
    val digits = (number.stringFromNumber(NSNumber(int = instant.nanosecondsOfSecond)) ?: "").trimEnd { it.toString() == zero }
    val formatter = NSDateFormatter().apply {
        this.locale = locale
        calendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian)
        gregorianStartDate = NSDate.distantPast
        timeZone = zone.toNSTimeZone()
    }
    val era = if (local.year <= 0) "G" else ""
    formatter.setLocalizedDateFormatFromTemplate("${era}yMMMd")
    val dateText = formatter.stringFromDate(date)
    val hour = when (use24HourClock) { true -> "H"; false -> "h"; null -> "j" }
    val seconds = if (local.second != 0 || instant.nanosecondsOfSecond != 0) "ss" else ""
    formatter.setLocalizedDateFormatFromTemplate("${hour}mm$seconds")
    formatter.dateFormat = withSecondFraction(formatter.dateFormat, if (digits.isEmpty()) "" else (number.decimalSeparator ?: ".") + digits)
    val time = formatter.stringFromDate(date)
    val hasLocation = '/' in zone.id && !zone.id.startsWith("Etc/")
    formatter.dateFormat = "VVV"
    val city = if (hasLocation) formatter.stringFromDate(date) else null
    formatter.dateFormat = "z"
    val name = if (hasLocation) formatter.stringFromDate(date) else utcOffsetLabel(zone.offsetAt(instant).totalSeconds)
    return WalletDateTimeText(dateText, time, timeZoneLabel(city, name))
}
