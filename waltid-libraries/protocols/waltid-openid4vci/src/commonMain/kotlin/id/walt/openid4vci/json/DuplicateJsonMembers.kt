package id.walt.openid4vci.json

import kotlinx.serialization.json.Json

/**
 * Returns true when [raw] is a JSON text whose objects contain a repeated member name.
 * Rejects containers deeper than 128 levels before the JSON tree parser runs.
 * Malformed strings and excessive nesting throw [IllegalArgumentException].
 */
internal fun jsonHasDuplicateMembers(raw: String): Boolean =
    JsonMemberScanner(raw).hasDuplicateMembers()

private class JsonMemberScanner(private val raw: String) {
    private var index = 0

    fun hasDuplicateMembers(): Boolean {
        skipWhitespace()
        if (index >= raw.length) return false
        val duplicate = parseValue(0)
        skipWhitespace()
        return duplicate
    }

    private fun parseValue(depth: Int): Boolean {
        skipWhitespace()
        if (index >= raw.length) return false
        return when (raw[index]) {
            '{' -> {
                require(depth < 128) { "JSON nesting exceeds 128 containers" }
                parseObject(depth + 1)
            }
            '[' -> {
                require(depth < 128) { "JSON nesting exceeds 128 containers" }
                parseArray(depth + 1)
            }
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

    private fun parseObject(depth: Int): Boolean {
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
            if (parseValue(depth)) return true
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

    private fun parseArray(depth: Int): Boolean {
        index++
        skipWhitespace()
        if (index < raw.length && raw[index] == ']') {
            index++
            return false
        }
        while (index < raw.length) {
            if (parseValue(depth)) return true
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
        val start = index++
        while (index < raw.length) {
            when (raw[index++]) {
                '"' -> return Json.decodeFromString<String>(raw.substring(start, index))
                '\\' -> index++
            }
        }
        throw IllegalArgumentException("Unterminated JSON string")
    }

    private fun skipLiteral() {
        while (index < raw.length && raw[index] !in ",}]") index++
    }

    private fun skipWhitespace() {
        while (index < raw.length && raw[index].isWhitespace()) index++
    }
}
