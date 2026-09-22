package id.walt.openid4vci.requests.notification

/**
 * Returns true when [raw] is a JSON text whose objects contain a repeated member name.
 * Malformed JSON returns false so the notification parser can report `invalid_notification_request`.
 */
internal fun jsonHasDuplicateMembers(raw: String): Boolean = try {
    JsonMemberScanner(raw).hasDuplicateMembers()
} catch (_: Exception) {
    false
}

private class JsonMemberScanner(private val raw: String) {
    private var index = 0

    fun hasDuplicateMembers(): Boolean {
        skipWhitespace()
        if (index >= raw.length) return false
        val duplicate = parseValue()
        skipWhitespace()
        return duplicate
    }

    private fun parseValue(): Boolean {
        skipWhitespace()
        if (index >= raw.length) return false
        return when (raw[index]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> {
                parseString()
                false
            }
            else -> {
                skipLiteral()
                false
            }
        }
    }

    private fun parseObject(): Boolean {
        index++
        val seen = mutableSetOf<String>()
        skipWhitespace()
        if (index < raw.length && raw[index] == '}') {
            index++
            return false
        }
        while (index < raw.length) {
            skipWhitespace()
            if (index >= raw.length || raw[index] != '"') return false
            val key = parseString()
            if (!seen.add(key)) return true
            skipWhitespace()
            if (index >= raw.length || raw[index] != ':') return false
            index++
            if (parseValue()) return true
            skipWhitespace()
            if (index >= raw.length) return false
            when (raw[index]) {
                ',' -> index++
                '}' -> {
                    index++
                    return false
                }
                else -> return false
            }
        }
        return false
    }

    private fun parseArray(): Boolean {
        index++
        skipWhitespace()
        if (index < raw.length && raw[index] == ']') {
            index++
            return false
        }
        while (index < raw.length) {
            if (parseValue()) return true
            skipWhitespace()
            if (index >= raw.length) return false
            when (raw[index]) {
                ',' -> index++
                ']' -> {
                    index++
                    return false
                }
                else -> return false
            }
        }
        return false
    }

    private fun parseString(): String {
        index++
        val value = StringBuilder()
        while (index < raw.length) {
            val character = raw[index]
            index++
            when (character) {
                '"' -> return value.toString()
                '\\' -> {
                    if (index >= raw.length) return value.toString()
                    val escaped = raw[index]
                    index++
                    value.append(
                        when (escaped) {
                            'u' -> {
                                val hex = raw.substring(index, (index + 4).coerceAtMost(raw.length))
                                index += hex.length
                                hex.toIntOrNull(16)?.toChar() ?: '?'
                            }
                            else -> escaped
                        }
                    )
                }
                else -> value.append(character)
            }
        }
        return value.toString()
    }

    private fun skipLiteral() {
        while (index < raw.length && raw[index] !in ",}]") index++
    }

    private fun skipWhitespace() {
        while (index < raw.length && raw[index].isWhitespace()) index++
    }
}
