package id.walt.credentials.display

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A CSS Color Level 3 sRGB color with 8-bit channels.
 *
 * @property red Red channel in `0..255`.
 * @property green Green channel in `0..255`.
 * @property blue Blue channel in `0..255`.
 * @property alpha Alpha channel in `0..255`. Opaque when omitted.
 */
public data class CssColor(
    public val red: Int,
    public val green: Int,
    public val blue: Int,
    public val alpha: Int = 255,
) {
    init {
        require(red in 0..255 && green in 0..255 && blue in 0..255 && alpha in 0..255) {
            "CSS color channels must be in 0..255"
        }
    }

    /** Packed 24-bit RGB, ignoring alpha. */
    public val rgb24: Int get() = (red shl 16) or (green shl 8) or blue
}

/**
 * Parses OID4VCI `background_color` / `text_color` values as CSS Color Level 3.
 *
 * Accepts `#rgb`, `#rrggbb`, `rgb()`, `rgba()`, `hsl()`, `hsla()`, and `transparent`.
 * Eight-digit hex is CSS Color Level 4 and is rejected.
 */
public object CssColors {
    /** Parses a CSS Color Level 3 token, or `null` when the value is absent or unsupported. */
    public fun parse(value: String?): CssColor? {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when {
            raw.equals("transparent", ignoreCase = true) -> CssColor(0, 0, 0, alpha = 0)
            raw.startsWith("#") -> parseHex(raw)
            functionNameEquals(raw, "rgba") -> parseRgbFunction(raw, alpha = true)
            functionNameEquals(raw, "rgb") -> parseRgbFunction(raw, alpha = false)
            functionNameEquals(raw, "hsla") -> parseHslFunction(raw, alpha = true)
            functionNameEquals(raw, "hsl") -> parseHslFunction(raw, alpha = false)
            else -> null
        }
    }

    private fun parseHex(value: String): CssColor? {
        if (!value.startsWith("#")) return null
        val hex = value.drop(1)
        val normalized = when (hex.length) {
            3 -> hex.map { "$it$it" }.joinToString("")
            6 -> hex
            else -> return null
        }
        if (normalized.any { !it.isHexDigit() }) return null
        val rgb = normalized.toLongOrNull(16) ?: return null
        return CssColor(
            red = ((rgb shr 16) and 0xFF).toInt(),
            green = ((rgb shr 8) and 0xFF).toInt(),
            blue = (rgb and 0xFF).toInt(),
        )
    }

    private fun parseRgbFunction(value: String, alpha: Boolean): CssColor? {
        val parts = functionArguments(value) ?: return null
        if (parts.size != if (alpha) 4 else 3) return null
        val red = parseRgbChannel(parts[0]) ?: return null
        val green = parseRgbChannel(parts[1]) ?: return null
        val blue = parseRgbChannel(parts[2]) ?: return null
        val parsedAlpha = if (alpha) parseAlpha(parts[3]) ?: return null else 255
        return CssColor(red, green, blue, parsedAlpha)
    }

    private fun parseHslFunction(value: String, alpha: Boolean): CssColor? {
        val parts = functionArguments(value) ?: return null
        if (parts.size != if (alpha) 4 else 3) return null
        val hue = parts[0].trim().removeSuffix("deg").toDoubleOrNull() ?: return null
        val saturation = parsePercentUnit(parts[1]) ?: return null
        val lightness = parsePercentUnit(parts[2]) ?: return null
        val parsedAlpha = if (alpha) parseAlpha(parts[3]) ?: return null else 255
        val (red, green, blue) = hslToRgb(hue, saturation, lightness)
        return CssColor(red, green, blue, parsedAlpha)
    }

    private fun functionNameEquals(value: String, name: String): Boolean {
        val open = value.indexOf('(')
        if (open <= 0) return false
        return value.substring(0, open).trim().equals(name, ignoreCase = true)
    }

    private fun functionArguments(value: String): List<String>? {
        val open = value.indexOf('(')
        if (open <= 0) return null
        val close = value.indexOf(')', startIndex = open + 1)
        if (close < 0) return null
        if (value.substring(close + 1).isNotBlank()) return null
        return value.substring(open + 1, close).split(',').map { it.trim() }
    }

    private fun parseRgbChannel(value: String): Int? {
        val raw = value.trim()
        return if (raw.endsWith("%")) {
            val percent = raw.dropLast(1).trim().toDoubleOrNull() ?: return null
            ((percent / 100.0) * 255.0).roundToInt().coerceIn(0, 255)
        } else {
            raw.toDoubleOrNull()?.roundToInt()?.coerceIn(0, 255)
        }
    }

    private fun parsePercentUnit(value: String): Double? {
        val raw = value.trim()
        if (!raw.endsWith("%")) return null
        return (raw.dropLast(1).trim().toDoubleOrNull() ?: return null).coerceIn(0.0, 100.0) / 100.0
    }

    private fun parseAlpha(value: String): Int? {
        val raw = value.trim()
        val unit = if (raw.endsWith("%")) {
            (raw.dropLast(1).trim().toDoubleOrNull() ?: return null) / 100.0
        } else {
            raw.toDoubleOrNull() ?: return null
        }
        return (unit.coerceIn(0.0, 1.0) * 255.0).roundToInt()
    }

    private fun hslToRgb(hue: Double, saturation: Double, lightness: Double): Triple<Int, Int, Int> {
        val h = ((hue % 360.0) + 360.0) % 360.0
        val c = (1.0 - abs(2.0 * lightness - 1.0)) * saturation
        val x = c * (1.0 - abs((h / 60.0) % 2.0 - 1.0))
        val m = lightness - c / 2.0
        val (r1, g1, b1) = when {
            h < 60.0 -> Triple(c, x, 0.0)
            h < 120.0 -> Triple(x, c, 0.0)
            h < 180.0 -> Triple(0.0, c, x)
            h < 240.0 -> Triple(0.0, x, c)
            h < 300.0 -> Triple(x, 0.0, c)
            else -> Triple(c, 0.0, x)
        }
        return Triple(
            ((r1 + m) * 255.0).roundToInt().coerceIn(0, 255),
            ((g1 + m) * 255.0).roundToInt().coerceIn(0, 255),
            ((b1 + m) * 255.0).roundToInt().coerceIn(0, 255),
        )
    }

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}
