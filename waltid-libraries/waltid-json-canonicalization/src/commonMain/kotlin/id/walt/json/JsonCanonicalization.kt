package id.walt.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** RFC 8785 canonical JSON, shared by DID and JAdES to keep cryptographic inputs identical. */
object JsonCanonicalization {
    /** JSON Canonicalization Scheme (RFC 8785) (RFC 8785 3.2). */
    fun canonicalize(element: JsonElement): String {
        val formatter = jsonNumberFormatter()
        return StringBuilder().also { appendCanonical(it, element, formatter) }.toString()
    }

    fun getCanonicalString(json: String): String {
        require(!jsonHasDuplicateMembers(json)) { "Duplicate JSON member" }
        return canonicalize(Json.parseToJsonElement(json))
    }
    fun getCanonicalBytes(json: String): ByteArray = getCanonicalString(json).encodeToByteArray()

    private fun appendCanonical(sb: StringBuilder, element: JsonElement, number: (Double) -> String) {
        when (element) {
            is JsonNull -> sb.append("null")
            is JsonObject -> {
                sb.append('{')
                element.entries.sortedWith { a, b -> a.key.compareTo(b.key) }.forEachIndexed { i, (k, v) ->
                    if (i > 0) sb.append(',')
                    appendString(sb, k); sb.append(':'); appendCanonical(sb, v, number)
                }
                sb.append('}')
            }

            is JsonArray -> {
                sb.append('[')
                element.forEachIndexed { i, v -> if (i > 0) sb.append(','); appendCanonical(sb, v, number) }
                sb.append(']')
            }

            is JsonPrimitive -> when {
                element.isString -> appendString(sb, element.content)
                element.booleanOrNull != null -> sb.append(element.content)
                else -> {
                    val value = element.content.toDouble()
                    require(value.isFinite()) { "Non-finite numbers cannot be canonicalized" }
                    sb.append(number(value))
                }
            }
        }
    }

    private fun appendString(sb: StringBuilder, s: String) {
        sb.append('"')
        for ((index, c) in s.withIndex()) {
            require(!c.isHighSurrogate() || s.getOrNull(index + 1)?.isLowSurrogate() == true) { "Unpaired Unicode surrogate" }
            require(!c.isLowSurrogate() || s.getOrNull(index - 1)?.isHighSurrogate() == true) { "Unpaired Unicode surrogate" }
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000c' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append("\\u").append(c.code.toString(16).padStart(4, '0')) else sb.append(c)
            }
        }
        sb.append('"')
    }

}

/** One formatter per document; native ECMAScript contexts are never shared across threads. */
internal expect fun jsonNumberFormatter(): (Double) -> String
