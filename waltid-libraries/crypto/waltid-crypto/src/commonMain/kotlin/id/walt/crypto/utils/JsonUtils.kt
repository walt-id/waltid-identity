package id.walt.crypto.utils

import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.cbor.*
import kotlinx.serialization.json.*
import kotlinx.serialization.serializerOrNull
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

@Suppress("NON_EXPORTABLE_TYPE")
@OptIn(ExperimentalJsExport::class)
@JsExport
object JsonUtils {

    internal val prettyJson by lazy { Json { prettyPrint = true } }

    @OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
    fun Any?.toJsonElement(): JsonElement =
        when (this) {
            is JsonElement -> this
            null -> JsonNull

            is CborElement -> when (this) {
                is CborNull -> JsonNull
                is CborBoolean -> JsonPrimitive(this.value)
                is CborInteger -> JsonPrimitive(this.long)
                is CborFloat -> JsonPrimitive(this.value)
                is CborString -> JsonPrimitive(this.value)
                is CborByteString -> JsonArray(JsonByteArray(this.toByteArray().copyOf()))
                is CborArray -> integerValuesOrNull()
                    ?.let { JsonArray(JsonLongArray(it)) }
                    ?: JsonArray(this.map { it.toJsonElement() })
                is CborMap -> {
                    // We must unwrap the CborElement key into a standard Kotlin String
                    val jsonEntries = this.entries.associate { (cborKey, cborValue) ->
                        val keyString = when (cborKey) {
                            is CborString -> cborKey.value         // Extract "address" from CborString
                            is CborInteger -> cborKey.long.toString() // e.g., if a map uses int keys
                            else -> cborKey.toString()             // Fallback
                        }
                        keyString to cborValue.toJsonElement()
                    }
                    JsonObject(jsonEntries)
                }

                is CborUndefined -> JsonNull
            }

            is String -> JsonPrimitive(this)
            is Boolean -> JsonPrimitive(this)
            is Number -> JsonPrimitive(this)

            is UByte -> JsonPrimitive(this)
            is UInt -> JsonPrimitive(this)
            is ULong -> JsonPrimitive(this)
            is UShort -> JsonPrimitive(this)

            is Map<*, *> -> JsonObject(map { Pair(it.key.toString(), it.value.toJsonElement()) }.toMap())
            is List<*> -> JsonArray(map { it.toJsonElement() })
            is Array<*> -> JsonArray(map { it.toJsonElement() })
            is Collection<*> -> JsonArray(map { it.toJsonElement() })
            is Enum<*> -> JsonPrimitive(this.toString())
            is Unit -> JsonPrimitive("null")

            else -> throw IllegalArgumentException("Cannot convert to JsonElement - Unknown type: ${this::class.simpleName}, was: $this")
        }

    fun javaToJsonElement(any: Any?) = any.toJsonElement()

    @OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
    fun Any?.toSerializedJsonElement(): JsonElement =
        when (this) {
            is JsonElement -> this
            is CborElement -> this.toJsonElement()
            null -> JsonNull
            is String -> JsonPrimitive(this)
            is Boolean -> JsonPrimitive(this)
            is Number -> JsonPrimitive(this)

            is UByte -> JsonPrimitive(this)
            is UInt -> JsonPrimitive(this)
            is ULong -> JsonPrimitive(this)
            is UShort -> JsonPrimitive(this)

            is Map<*, *> -> JsonObject(map { Pair(it.key.toString(), it.value.toSerializedJsonElement()) }.toMap())
            is List<*> -> JsonArray(map { it.toSerializedJsonElement() })
            is Array<*> -> JsonArray(map { it.toSerializedJsonElement() })
            is Collection<*> -> JsonArray(map { it.toSerializedJsonElement() })
            is Unit -> JsonPrimitive("null")
            else -> {
                val serializer = this::class.serializerOrNull()

                if (serializer != null) {
                    @Suppress("UNCHECKED_CAST")
                    Json.encodeToJsonElement(serializer as KSerializer<Any>, this)
                } else when (this) {
                    is Enum<*> -> JsonPrimitive(this.toString())
                    else -> throw IllegalArgumentException("Cannot convert to JsonElement - Unknown type: ${this::class.simpleName}, was: $this")
                }
            }
        }


    @JsName("listToJsonElement")
    fun List<*>.toJsonElement(): JsonElement {
        // A CborArray is itself a List, so an unqualified call on a statically CBOR-typed receiver lands here
        // rather than in the CBOR branch above, and would convert element by element. Route it back so that a
        // large array of numbers keeps its flyweight regardless of which overload the call site picked.
        if (this is CborArray) return (this as Any?).toJsonElement()
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

// Preserve the existing signed-byte JSON representation without allocating a number and its
// decimal string for every byte of a binary value. The private snapshot also keeps
// the resulting JsonArray immutable if the source byte string was backed by a mutable array.
private class JsonByteArray(private val bytes: ByteArray) : AbstractList<JsonElement>() {
    override val size: Int get() = bytes.size
    override fun get(index: Int): JsonElement = ByteValues[bytes[index].toInt() + 128]

    private companion object {
        val ByteValues = List(256) { JsonPrimitive(it - 128) }
    }
}

/**
 * The same treatment for an array of whole numbers, which is what a binary claim looks like when it reaches
 * CBOR as an array rather than a byte string.
 *
 * A heap dump of a verifier holding two presentations of a 230,000 element claim showed 920,667 live
 * `JsonLiteral`, 1,066,730 `String` and 1,076,286 `byte[]` instances - two objects per element, per copy,
 * retained for as long as the session was. Wrapping the values costs one object regardless of length; the
 * `JsonPrimitive` for an element is created only if something actually reads that element.
 */
private class JsonLongArray(private val values: LongArray) : AbstractList<JsonElement>() {
    override val size: Int get() = values.size
    override fun get(index: Int): JsonElement = JsonPrimitive(values[index])
}

/**
 * The values of a CBOR array if every element is an untagged integer, else null.
 *
 * Only allocates the result, so an array that turns out to be mixed costs one pass and nothing else.
 */
private fun CborArray.integerValuesOrNull(): LongArray? {
    if (isEmpty()) return null
    forEach { if (it !is CborInteger) return null }
    return LongArray(size) { (this[it] as CborInteger).long }
}

/** Text longer than this is stored as a descriptor. 1 KB keeps every human-readable claim whole. */
const val MAX_STORED_JSON_TEXT: Int = 1024

/** Arrays longer than this are stored as a descriptor. 64 keeps short arrays and coordinate pairs whole. */
const val MAX_STORED_JSON_ARRAY: Int = 64

/**
 * A copy of this element with bulk leaves replaced by `{type, length, truncated, prefix}` descriptors.
 *
 * Lives beside [JsonByteArray] because this is the fix for what that creates. A CBOR byte string becomes
 * one JSON number per byte (see `toJsonElement`), which costs nothing in memory - [JsonByteArray] is a
 * facade over the bytes - but on **serialisation** a 250 KB portrait becomes a 250,000 element array:
 * 912,135 characters of JSON, and 3,232,078 bytes of BSON once Mongo adds a key and a type tag per
 * element. Measured, not estimated.
 *
 * Changing the representation itself is not an option: `toJsonElement` has ~450 call sites and a test
 * pins the byte-per-number form deliberately, because consumers round-trip CBOR through JSON. So the
 * bulk is bounded where it is **persisted** instead, and every store that keeps a decoded copy beside
 * the encoded original should call this.
 *
 * Small values survive verbatim - they are what DCQL matches on and what diagnoses a serialisation
 * problem. Only bulk is replaced, and the descriptor keeps the length so the loss is visible.
 */
fun JsonElement.withoutBulkValues(
    maxText: Int = MAX_STORED_JSON_TEXT,
    maxArray: Int = MAX_STORED_JSON_ARRAY,
): JsonElement = when (this) {
    is JsonPrimitive ->
        if (isString && content.length > maxText) buildJsonObject {
            put("type", "string")
            put("length", content.length)
            put("truncated", true)
            put("prefix", content.take(maxText))
        } else this

    is JsonArray ->
        if (size > maxArray) buildJsonObject {
            put("type", "array")
            put("length", size)
            put("truncated", true)
            put("prefix", JsonArray(take(maxArray).map { it.withoutBulkValues(maxText, maxArray) }))
        } else JsonArray(map { it.withoutBulkValues(maxText, maxArray) })

    // Objects are walked rather than bounded by key count: the shape of a claim set is the useful part,
    // and it is the leaves that carry a portrait.
    is JsonObject -> JsonObject(mapValues { (_, value) -> value.withoutBulkValues(maxText, maxArray) })
}

/** Byte arrays shorter than this stay arrays: a digest or a coordinate pair is easier to read that way. */
const val MIN_BASE64_BYTE_ARRAY: Int = 64

/** Marks a byte array that was rewritten as base64url, so a reader can restore it exactly. */
const val BASE64_BYTES_TYPE: String = "bytes-base64url"

/**
 * A copy of this element with long byte arrays rewritten as base64url, losslessly.
 *
 * A CBOR byte string is decoded to one JSON number per byte (see `toJsonElement`), and every store then
 * pays per element. Measured for a 250 KB portrait, encoded as BSON:
 *
 * | representation | bytes |
 * |---|---|
 * | array of numbers, as decoded | 2,888,910 |
 * | Kotlin `ByteArray` through the standard codec | 2,888,910 (the codec writes an array of ints) |
 * | base64url string | 333,354 |
 *
 * So this is an 8.7x reduction for image-bearing credentials, and it needs no custom BSON encoder, which
 * keeps it working on every supported store rather than MongoDB alone.
 *
 * Lossless and reversible: an array qualifies only if every element is an integer in the signed byte
 * range, and the result records the original length. Nothing outside that range is touched, so an array
 * of larger numbers keeps its shape.
 *
 * This does not change what a wallet sends or what is signed. The encoded credential is kept verbatim
 * elsewhere in the session; this only affects the decoded copy that is stored beside it.
 */
fun JsonElement.withByteArraysAsBase64(minLength: Int = MIN_BASE64_BYTE_ARRAY): JsonElement = when (this) {
    is JsonPrimitive -> this

    is JsonArray ->
        signedBytesOrNull(minLength)?.let { bytes ->
            buildJsonObject {
                put("type", BASE64_BYTES_TYPE)
                put("length", bytes.size)
                put("base64url", bytes.encodeToBase64Url())
            }
        } ?: JsonArray(map { it.withByteArraysAsBase64(minLength) })

    is JsonObject -> JsonObject(mapValues { (_, value) -> value.withByteArraysAsBase64(minLength) })
}

/** The array as signed bytes when every element is one, else null. One pass, allocates only on success. */
private fun JsonArray.signedBytesOrNull(minLength: Int): ByteArray? {
    if (size < minLength) return null
    forEach { element ->
        val value = (element as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toIntOrNull() ?: return null
        if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) return null
    }
    return ByteArray(size) { ((this[it] as JsonPrimitive).content.toInt()).toByte() }
}
