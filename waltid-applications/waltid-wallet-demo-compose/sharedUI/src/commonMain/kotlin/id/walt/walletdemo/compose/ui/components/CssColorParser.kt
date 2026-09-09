package id.walt.walletdemo.compose.ui.components

internal data class CssColor(
    val red: Int,
    val green: Int,
    val blue: Int,
    val alpha: Int = 255,
)

internal object CssColorParser {
    fun parse(value: String?): CssColor? {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when {
            raw.equals("transparent", ignoreCase = true) -> CssColor(0, 0, 0, alpha = 0)
            raw.startsWith("#") -> parseHex(raw)
            raw.startsWith("rgba", ignoreCase = true) -> parseRgb(raw, withAlpha = true)
            raw.startsWith("rgb", ignoreCase = true) -> parseRgb(raw, withAlpha = false)
            else -> null
        }
    }

    private fun parseHex(value: String): CssColor? {
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

    private fun parseRgb(value: String, withAlpha: Boolean): CssColor? {
        val open = value.indexOf('(')
        val close = value.indexOf(')')
        if (open <= 0 || close <= open) return null
        val parts = value.substring(open + 1, close).split(',').map { it.trim() }
        if (parts.size != if (withAlpha) 4 else 3) return null
        val red = parts[0].toIntOrNull()?.coerceIn(0, 255) ?: return null
        val green = parts[1].toIntOrNull()?.coerceIn(0, 255) ?: return null
        val blue = parts[2].toIntOrNull()?.coerceIn(0, 255) ?: return null
        val alpha = if (withAlpha) {
            ((parts[3].toFloatOrNull() ?: return null).coerceIn(0f, 1f) * 255f).toInt()
        } else {
            255
        }
        return CssColor(red, green, blue, alpha)
    }

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}
