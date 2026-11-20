package id.walt.walletdemo.compose.logic

import kotlin.time.Instant

/** RFC 7519 seconds, including decimals/exponents; never guess milliseconds from magnitude. */
internal object NumericDateParser {
    private val number = Regex("(-?)([0-9]+)(?:\\.([0-9]+))?(?:[eE]([+-]?[0-9]+))?")

    fun parse(value: String): Instant? {
        val raw = value.trim()
        // Bound work on untrusted input. Unrepresentable values remain visible in their raw form.
        if (raw.length > 64) return null
        val match = number.matchEntire(raw) ?: return null
        val (sign, integer, fraction, exponentText) = match.destructured
        val exponent = if (exponentText.isEmpty()) 0 else exponentText.toIntOrNull() ?: return null
        if (exponent !in -64..64) return null
        val digits = integer + fraction
        val point = integer.length + exponent
        val whole = when {
            point <= 0 -> "0"
            point >= digits.length -> digits.padEnd(point, '0')
            else -> digits.take(point)
        }.toLongOrNull() ?: return null
        val decimal = when {
            point <= 0 -> "0".repeat(-point) + digits
            point >= digits.length -> ""
            else -> digits.drop(point)
        }.trimEnd('0')
        if (decimal.length > 9) return null
        val nanos = decimal.padEnd(9, '0').toInt()
        // Four-digit Gregorian years are readable on every renderer; do not clamp or wrap.
        val seconds = if (sign == "-") -whole - if (nanos > 0) 1 else 0 else whole
        if (seconds !in -62_135_596_800L..253_402_300_799L) return null
        val positiveNanos = if (sign == "-" && nanos > 0) 1_000_000_000 - nanos else nanos
        return Instant.fromEpochSeconds(seconds, positiveNanos)
    }
}
