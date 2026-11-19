package id.walt.ades.jades

import kotlin.io.encoding.Base64
import kotlin.math.abs
import kotlin.math.floor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

/** Base64 / JSON helpers shared by the JAdES signer and validator. */
internal object JadesEncoding {
    private val urlSafe = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    private val urlSafeLenient = Base64.UrlSafe.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)
    private val standardLenient = Base64.Default.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)

    val json = Json { prettyPrint = false }

    fun b64url(bytes: ByteArray): String = urlSafe.encode(bytes)
    fun b64url(text: String): String = urlSafe.encode(text.encodeToByteArray())
    fun b64(bytes: ByteArray): String = Base64.Default.encode(bytes)

    /** Strict base64url decoding (JWS segments). */
    fun fromB64url(value: String): ByteArray = urlSafeLenient.decode(value)

    /** Lenient decoding for values that are base64 or base64url depending on the producer (pkiOb, kid, digVal, ...). */
    fun fromAnyBase64(value: String): ByteArray {
        val v = value.filterNot { it.isWhitespace() }
        return if (v.any { it == '-' || it == '_' }) urlSafeLenient.decode(v) else runCatching { standardLenient.decode(v) }.getOrElse { urlSafeLenient.decode(v) }
    }

    fun encode(element: JsonElement): String = json.encodeToString(JsonElement.serializer(), element)
    fun parseObject(text: String): JsonObject = json.parseToJsonElement(text) as? JsonObject ?: throw IllegalArgumentException("JSON object expected")

    /** JSON Canonicalization Scheme (RFC 8785) for clear etsiU incorporation (canonAlg = [JadesHeaders.CANON_JCS]). */
    fun canonicalize(element: JsonElement): String = StringBuilder().also { appendCanonical(it, element) }.toString()

    private fun appendCanonical(sb: StringBuilder, element: JsonElement) {
        when (element) {
            is JsonNull -> sb.append("null")
            is JsonObject -> {
                sb.append('{')
                element.entries.sortedWith { a, b -> a.key.compareTo(b.key) }.forEachIndexed { i, (k, v) ->
                    if (i > 0) sb.append(',')
                    appendString(sb, k); sb.append(':'); appendCanonical(sb, v)
                }
                sb.append('}')
            }

            is JsonArray -> {
                sb.append('[')
                element.forEachIndexed { i, v -> if (i > 0) sb.append(','); appendCanonical(sb, v) }
                sb.append(']')
            }

            is JsonPrimitive -> when {
                element.isString -> appendString(sb, element.content)
                element.booleanOrNull != null -> sb.append(element.content)
                element.longOrNull != null -> sb.append(element.longOrNull.toString())
                else -> sb.append(canonicalNumber(element.content.toDouble()))
            }
        }
    }

    private fun canonicalNumber(d: Double): String {
        require(d.isFinite()) { "Non-finite numbers cannot be canonicalized" }
        if (d == 0.0) return "0"
        if (d == floor(d) && abs(d) < 1e21) return d.toLong().toString()
        return d.toString().replace("E", "e").let { s -> if ("e" in s && !s.contains("e-")) s.replace("e", "e+") else s }
    }

    private fun appendString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
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

    fun concat(vararg parts: ByteArray): ByteArray = concat(parts.toList())
    fun concat(parts: List<ByteArray>): ByteArray {
        val out = ByteArray(parts.sumOf { it.size })
        var pos = 0
        for (p in parts) {
            p.copyInto(out, pos); pos += p.size
        }
        return out
    }
}
