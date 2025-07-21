package id.walt.crypto.utils

import kotlinx.serialization.json.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

@Suppress("NON_EXPORTABLE_TYPE")
@OptIn(ExperimentalJsExport::class)
@JsExport
object JsonUtils {

    internal val prettyJson by lazy { Json { prettyPrint = true } }

    fun Any?.toJsonElement(): JsonElement =
        when (this) {
            is JsonElement -> this
            null -> JsonNull
            is String -> JsonPrimitive(this)
            is Boolean -> JsonPrimitive(this)
            is Number -> JsonPrimitive(this)
            /*
            is UByte -> JsonPrimitive(this)
            is UInt -> JsonPrimitive(this)
            is ULong -> JsonPrimitive(this)
            is UShort -> JsonPrimitive(this)
            */
            is Map<*, *> -> JsonObject(map { Pair(it.key.toString(), it.value.toJsonElement()) }.toMap())
            is List<*> -> JsonArray(map { it.toJsonElement() })
            is Array<*> -> JsonArray(map { it.toJsonElement() })
            is Collection<*> -> JsonArray(map { it.toJsonElement() })
            is Enum<*> -> JsonPrimitive(this.toString())
            is Unit -> JsonPrimitive("null")
            else -> throw IllegalArgumentException("Cannot convert to JsonElement - Unknown type: ${this::class.simpleName}, was: $this")
        }

    fun javaToJsonElement(any: Any?) = any.toJsonElement()

    @JsName("listToJsonElement")
    fun List<*>.toJsonElement(): JsonElement {
        return JsonArray(map { it.toJsonElement() })
    }

    @JsName("mapToJsonObject")
    fun Map<*, *>.toJsonElement(): JsonElement {
        val map: MutableMap<String, JsonElement> = mutableMapOf()
        this.forEach { (key, value) ->
            map[key as String] = value.toJsonElement()
        }
        return JsonObject(map)
    }

    fun Map<*, *>.toJsonObject() = this.toJsonElement().jsonObject
    fun javaToJsonObject(map: Map<*, *>) = map.toJsonObject()

    private fun toHexChar(i: Int): Char {
        val d = i and 0xf
        return if (d < 10) (d + '0'.code).toChar()
        else (d - 10 + 'a'.code).toChar()
    }

    private val ESCAPE_STRINGS: Array<String?> = arrayOfNulls<String>(93).apply {
        for (c in 0..0x1f) {
            val c1 = toHexChar(c shr 12)
            val c2 = toHexChar(c shr 8)
            val c3 = toHexChar(c shr 4)
            val c4 = toHexChar(c)
            this[c] = "\\u$c1$c2$c3$c4"
        }
        this['"'.code] = "\\\""
        this['\\'.code] = "\\\\"
        this['\t'.code] = "\\t"
        this['\b'.code] = "\\b"
        this['\n'.code] = "\\n"
        this['\r'.code] = "\\r"
        this[0x0c] = "\\f"
    }

    private fun StringBuilder.printQuoted(value: String) {
        append('"')
        var lastPos = 0
        for (i in value.indices) {
            val c = value[i].code
            if (c < ESCAPE_STRINGS.size && ESCAPE_STRINGS[c] != null) {
                append(value, lastPos, i) // flush prev
                append(ESCAPE_STRINGS[c])
                lastPos = i + 1
            }
        }

        if (lastPos != 0) append(value, lastPos, value.length)
        else append(value)
        append('"')
    }

    fun Map<String, JsonElement>.printAsJson(): String =
        this.entries.joinToString(
            separator = ",",
            prefix = "{",
            postfix = "}",
            transform = { (k, v) ->
                buildString {
                    printQuoted(k)
                    append(':')
                    append(v)
                }
            }
        )

    fun stringToJsonPrimitive(value: String): JsonPrimitive {
        return JsonPrimitive(value)
    }
}
