@file:OptIn(ExperimentalForeignApi::class)

package id.walt.walletdemo.compose.logic

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toNSDate
import kotlinx.datetime.toNSTimeZone
import kotlin.time.Instant

internal actual fun platformFormatWalletInstant(instant: Instant, timeZoneId: String?, localeTag: String?, use24HourClock: Boolean?): String {
    val locale = localeTag?.let { NSLocale(it) } ?: NSLocale.currentLocale
    val zone = timeZoneId?.let(TimeZone::of) ?: TimeZone.currentSystemDefault()
    val date = Instant.fromEpochSeconds(instant.epochSeconds).toNSDate()
    val number = NSNumberFormatter().apply { this.locale = locale; minimumIntegerDigits = 9u; usesGroupingSeparator = false }
    val zero = NSNumberFormatter().apply { this.locale = locale }.stringFromNumber(NSNumber(int = 0)) ?: "0"
    val digits = (number.stringFromNumber(NSNumber(int = instant.nanosecondsOfSecond)) ?: "").trimEnd { it.toString() == zero }
    val formatter = NSDateFormatter().apply {
        this.locale = locale
        calendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian)
        gregorianStartDate = NSDate.distantPast
        timeZone = zone.toNSTimeZone()
        val hour = when (use24HourClock) { true -> "H"; false -> "h"; null -> "j" }
        setLocalizedDateFormatFromTemplate("yMMMd${hour}mmss")
        dateFormat = withSecondFraction(dateFormat, if (digits.isEmpty()) "" else (number.decimalSeparator ?: ".") + digits)
    }
    return "${formatter.stringFromDate(date)} ${utcOffsetLabel(zone.offsetAt(instant).totalSeconds)}"
}
