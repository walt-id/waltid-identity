@file:OptIn(ExperimentalWasmJsInterop::class)

package id.walt.walletdemo.compose.logic

import kotlin.time.Instant
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny

internal actual fun platformFormatWalletInstant(instant: Instant, timeZoneId: String?, localeTag: String?, use24HourClock: Boolean?): WalletDateTimeText {
    val result = formatInstant(instant.epochSeconds.toDouble() * 1000, instant.nanosecondsOfSecond, timeZoneId, localeTag,
        when (use24HourClock) { true -> "h23"; false -> "h12"; null -> null })
    return WalletDateTimeText(result.date, result.time, result.timeZone)
}

private external interface FormattedInstant : JsAny {
    val date: String
    val time: String
    val timeZone: String
}

@JsFun("""(milliseconds, nanos, timeZone, locale, hourCycle) => {
    const language = locale || undefined;
    // Resolve once, so every field uses the same device timezone snapshot.
    const zone = new Intl.DateTimeFormat(language, {timeZone: timeZone || undefined}).resolvedOptions().timeZone;
    const options = { year: 'numeric', month: 'short', day: 'numeric', calendar: 'gregory', timeZone: zone };
    const date = new Date(milliseconds);
    // A supported UTC year-1 instant can fall into BCE in the viewer's timezone.
    if (date.getUTCFullYear() <= 1) {
        const era = new Intl.DateTimeFormat('en-US', {year: 'numeric', era: 'short', calendar: 'gregory', timeZone: zone}).formatToParts(date).find(p => p.type === 'era').value;
        if (era === 'BC') options.era = 'short';
    }
    const clock = {hour: '2-digit', minute: '2-digit', timeZone: zone};
    if (hourCycle) clock.hourCycle = hourCycle;
    const seconds = new Intl.DateTimeFormat('en', {second: 'numeric', timeZone: zone}).format(date);
    if (Number(seconds) !== 0 || nanos !== 0) clock.second = '2-digit';
    const fraction = String(nanos).padStart(9, '0').replace(/0+$/, '');
    const digits = new Intl.NumberFormat(language, {useGrouping: false});
    const separator = new Intl.NumberFormat(language).formatToParts(1.1).find(p => p.type === 'decimal').value;
    const time = new Intl.DateTimeFormat(language, clock).formatToParts(date).map(p => {
        if (p.type === 'second' && fraction) return p.value + separator + [...fraction].map(c => digits.format(Number(c))).join('');
        return p.value;
    }).join('');
    const city = zone.includes('/') && !zone.startsWith('Etc/') ? zone.split('/').pop().replaceAll('_', ' ') : null;
    const name = new Intl.DateTimeFormat(language, {timeZone: zone, timeZoneName: 'short'}).formatToParts(date).find(p => p.type === 'timeZoneName').value;
    const fixed = new Intl.DateTimeFormat('en-US', {timeZone: zone, timeZoneName: 'longOffset'}).formatToParts(date).find(p => p.type === 'timeZoneName').value.replace('GMT', 'UTC');
    // Intl has no exemplar-city API: use CLDR's IANA-ID fallback, never a hand-maintained city/abbreviation table.
    return {date: new Intl.DateTimeFormat(language, options).format(date), time, timeZone: city ? city + ', ' + name : fixed === 'UTC+00:00' ? 'UTC' : fixed};
}""")
private external fun formatInstant(milliseconds: Double, nanos: Int, timeZone: String?, locale: String?, hourCycle: String?): FormattedInstant
