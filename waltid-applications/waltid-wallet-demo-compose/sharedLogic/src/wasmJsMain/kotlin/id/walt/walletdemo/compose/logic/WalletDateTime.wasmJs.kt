package id.walt.walletdemo.compose.logic

import kotlin.time.Instant
import kotlin.js.ExperimentalWasmJsInterop

internal actual fun platformFormatWalletInstant(instant: Instant, timeZoneId: String?, localeTag: String?, use24HourClock: Boolean?): String =
    formatInstant(instant.epochSeconds.toDouble() * 1000, instant.nanosecondsOfSecond, timeZoneId, localeTag,
        when (use24HourClock) { true -> "h23"; false -> "h12"; null -> null })

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("""(milliseconds, nanos, timeZone, locale, hourCycle) => {
    const options = { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit', calendar: 'gregory', timeZoneName: 'longOffset' };
    if (timeZone) options.timeZone = timeZone;
    if (hourCycle) options.hourCycle = hourCycle;
    const language = locale || undefined;
    const fraction = String(nanos).padStart(9, '0').replace(/0+$/, '');
    const digits = new Intl.NumberFormat(language, {useGrouping: false});
    const separator = new Intl.NumberFormat(language).formatToParts(1.1).find(p => p.type === 'decimal').value;
    return new Intl.DateTimeFormat(language, options).formatToParts(new Date(milliseconds)).map(p => {
        if (p.type === 'second' && fraction) return p.value + separator + [...fraction].map(c => digits.format(Number(c))).join('');
        if (p.type === 'timeZoneName') {
            // Use a stable UTC offset label; the offset is resolved for this instant, including DST.
            const offset = new Intl.DateTimeFormat('en-US', {timeZone: timeZone || undefined, timeZoneName: 'longOffset'}).formatToParts(new Date(milliseconds)).find(p => p.type === 'timeZoneName').value;
            return offset.replace('GMT', 'UTC');
        }
        return p.value;
    }).join('');
}""")
private external fun formatInstant(milliseconds: Double, nanos: Int, timeZone: String?, locale: String?, hourCycle: String?): String
