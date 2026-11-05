package id.walt.walletdemo.compose.logic

import android.text.format.DateFormat
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toJavaZoneId
import kotlinx.datetime.toLocalDateTime
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.time.Instant
import kotlin.time.toJavaInstant

internal actual fun platformFormatWalletInstant(instant: Instant, timeZoneId: String?, localeTag: String?, use24HourClock: Boolean?): WalletDateTimeText {
    val locale = localeTag?.let(Locale::forLanguageTag) ?: Locale.getDefault()
    val zone = timeZoneId?.let(TimeZone::of) ?: TimeZone.currentSystemDefault()
    val date = instant.toLocalDateTime(zone)
    val hour = when (use24HourClock) { true -> "H"; false -> "h"; null -> "j" }
    val seconds = if (date.second != 0 || instant.nanosecondsOfSecond != 0) "ss" else ""
    val pattern = if (use24HourClock == null) DateTimeFormatterBuilder.getLocalizedDateTimePattern(
        null, if (seconds.isEmpty()) FormatStyle.SHORT else FormatStyle.MEDIUM, IsoChronology.INSTANCE, locale,
    ) else DateFormat.getBestDateTimePattern(locale, "${hour}mm$seconds")
    val symbols = DecimalFormatSymbols.getInstance(locale)
    val digits = instant.nanosecondsOfSecond.toString().padStart(9, '0').trimEnd('0')
    val fraction = if (digits.isEmpty()) "" else symbols.decimalSeparator + digits.map { symbols.zeroDigit + (it - '0') }.joinToString("")
    val era = if (date.year <= 0) "G" else ""
    fun format(pattern: String) = DateTimeFormatter.ofPattern(pattern, locale)
        .withDecimalStyle(DecimalStyle.of(locale)).format(date.toJavaLocalDateTime())
    val city = timeZoneCity(zone.id)
    val specificName = if (city == null) utcOffsetLabel(zone.offsetAt(instant).totalSeconds)
        else DateTimeFormatter.ofPattern("z", locale).withZone(zone.toJavaZoneId()).format(instant.toJavaInstant())
    // kotlinx-datetime owns conversion and offsets; the platform supplies localized text only.
    return WalletDateTimeText(
        format(DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.MEDIUM, null,
            IsoChronology.INSTANCE, locale) + if (era.isEmpty()) "" else " G"),
        format(withSecondFraction(pattern, fraction)),
        timeZoneLabel(city, specificName),
    )
}
