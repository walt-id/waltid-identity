package id.walt.ades.jades

import id.walt.json.JsonCanonicalization

import kotlin.io.encoding.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

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
    fun canonicalize(element: JsonElement): String = JsonCanonicalization.canonicalize(element)

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
