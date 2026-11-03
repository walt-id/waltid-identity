package id.walt.walletdemo.compose.logic

import java.text.DecimalFormatSymbols
import android.text.format.DateFormat
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.time.Instant

internal actual fun platformFormatWalletInstant(instant: Instant, timeZoneId: String?, localeTag: String?, use24HourClock: Boolean?): String {
    val locale = localeTag?.let(Locale::forLanguageTag) ?: Locale.getDefault()
    val zone = timeZoneId?.let(TimeZone::of) ?: TimeZone.currentSystemDefault()
    val date = instant.toLocalDateTime(zone)
    val pattern = if (use24HourClock == null) {
        DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.MEDIUM, FormatStyle.MEDIUM, IsoChronology.INSTANCE, locale)
    } else {
        DateFormat.getBestDateTimePattern(locale, if (use24HourClock) "yMMMdHms" else "yMMMdhms")
    }
    val symbols = DecimalFormatSymbols.getInstance(locale)
    val digits = instant.nanosecondsOfSecond.toString().padStart(9, '0').trimEnd('0')
    val fraction = if (digits.isEmpty()) "" else symbols.decimalSeparator + digits.map { symbols.zeroDigit + (it - '0') }.joinToString("")
    val formatter = DateTimeFormatter.ofPattern(withSecondFraction(pattern, fraction), locale)
        .withDecimalStyle(DecimalStyle.of(locale))
    // kotlinx-datetime owns conversion and offsets; the platform supplies localized text only.
    return "${formatter.format(date.toJavaLocalDateTime())} ${utcOffsetLabel(zone.offsetAt(instant).totalSeconds)}"
}
