@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.credentials.signatures.sdjwt

import id.walt.crypto.utils.Base64Utils.encodeToBase64
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.kotlincrypto.hash.sha2.SHA256

/**
 * Serializer for the disclosure [SdJwtSelectiveDisclosure.location] Claim Path that is
 * backward-compatible with the legacy representation.
 *
 * - **Writes** the SD-JWT VC §4.6.1 Claim Path as a JSON array of components.
 * - **Reads** either that array, OR a legacy JSONPath string (e.g. `"$.birthdate"`,
 *   `"$.credentialSubject.degree.name"`, `"$.nationalities[0]"`) which is parsed into the
 *   equivalent Claim Path component list. This keeps already-persisted credentials and stored
 *   sessions deserializable after the migration from a string `location` to a Claim Path array.
 */
object ClaimPathSerializer : KSerializer<List<JsonElement>> {
    private val delegate = ListSerializer(JsonElement.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<JsonElement>) =
        delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): List<JsonElement> {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return delegate.deserialize(decoder)
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonArray -> element.toList()
            is JsonPrimitive -> if (element.isString) parseJsonPath(element.content) else listOf(element)
            else -> emptyList()
        }
    }

    /**
     * Parses a legacy JSONPath-ish string into Claim Path components. Strips a leading `$`,
     * splits on `.`, and turns `[n]` / `[*]` segments into integer indices / null wildcards.
     */
    private fun parseJsonPath(path: String): List<JsonElement> {
        val result = mutableListOf<JsonElement>()
        path.removePrefix("$").split('.').forEach { rawSegment ->
            if (rawSegment.isEmpty()) return@forEach
            // Separate a key from any trailing array-index/wildcard tokens, e.g. "nationalities[0]".
            val bracket = rawSegment.indexOf('[')
            val key = if (bracket >= 0) rawSegment.substring(0, bracket) else rawSegment
            if (key.isNotEmpty()) result.add(JsonPrimitive(key))
            if (bracket >= 0) {
                Regex("""\[([^\]]*)\]""").findAll(rawSegment.substring(bracket)).forEach { m ->
                    val token = m.groupValues[1]
                    val idx = token.toIntOrNull()
                    when {
                        token == "*" -> result.add(JsonNull)
                        idx != null -> result.add(JsonPrimitive(idx))
                        else -> result.add(JsonPrimitive(token))
                    }
                }
            }
        }
        return result
    }
}

@Serializable
data class SdJwtSelectiveDisclosure(
    val salt: String,
    /** claim name */
    val name: String?,
    /** claim value */
    val value: JsonElement,

    /**
     * Claim Path locating this disclosure's claim within the credential, per
     * SD-JWT VC (draft-ietf-oauth-sd-jwt-vc) §4.6.1.
     *
     * A non-empty array of path components, each being:
     *  - a [JsonPrimitive] string: selects the object key,
     *  - a [JsonPrimitive] non-negative integer: selects the array index,
     *  - [kotlinx.serialization.json.JsonNull]: selects all elements of the array (wildcard).
     *
     * The path is resolved against the credential root (the top-level claims object; for
     * W3C credentials embedded under a `vc` claim, relative to the `vc` content). It points
     * to the claim as if all selectively disclosable claims were disclosed (§4.6.1.2).
     *
     * `null` when the location has not (yet) been resolved. Deserialization is
     * backward-compatible with the legacy JSONPath *string* representation (see
     * [ClaimPathSerializer]).
     */
    @Serializable(ClaimPathSerializer::class)
    val location: List<JsonElement>? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    var encoded: String = makeEncoded(salt, name, value)
) {
    companion object {
        // If name is null, creates [salt, value] (size 2).
        // If name is present, creates [salt, name, value] (size 3).
        fun makeJsonArray(salt: String, name: String?, value: JsonElement): JsonArray {
            val content = mutableListOf<JsonElement>(JsonPrimitive(salt))
            if (name != null) {
                content.add(JsonPrimitive(name))
            }
            content.add(value)
            return JsonArray(content)
        }

        fun encodeJsonArray(jsonArray: JsonArray) = jsonArray.toString().encodeToByteArray().encodeToBase64Url()
        fun makeEncoded(salt: String, name: String?, value: JsonElement) = encodeJsonArray(makeJsonArray(salt, name, value))

        fun encodeJsonArray2(jsonArray: JsonArray) = jsonArray.toString().encodeToByteArray().encodeToBase64()
        fun makeEncoded2(salt: String, name: String?, value: JsonElement) = encodeJsonArray2(makeJsonArray(salt, name, value))
    }

    fun asJsonArray() = makeJsonArray(salt, name, value)
    fun asEncoded() = makeEncoded(salt, name, value)
    fun asEncoded2() = makeEncoded2(salt, name, value)
    fun asHashed() = SHA256().digest(asEncoded().encodeToByteArray()).encodeToBase64Url()
    fun asHashed2() = SHA256().digest(asEncoded2().encodeToByteArray()).encodeToBase64Url()
    fun asHashed3() = SHA256().digest(encoded.encodeToByteArray().encodeToBase64Url().encodeToByteArray()).encodeToBase64Url()

    /**
     * RFC 9901 §4.2 digest computed over the disclosure's preserved original wire encoding
     * (`SHA-256(encoded)`, base64url). This matches the digest the issuer placed in `_sd` when
     * [encoded] is the exact base64url string received on the wire, regardless of any byte-level
     * re-serialization differences in [asEncoded].
     */
    fun asHashedFromEncoded() = SHA256().digest(encoded.encodeToByteArray()).encodeToBase64Url()

    /**
     * Renders this disclosure's [location] Claim Path (SD-JWT VC §4.6.1) as a DIF Presentation
     * Exchange JSONPath string (e.g. `$.credentialSubject.degree.name`, `$.nationalities[0]`).
     *
     * Provided for interoperability with legacy components that select claims using DIF PE
     * `field.path` JSONPath strings. Wildcard (`null`) path components are rendered as `[*]`.
     * Returns `null` when [location] has not been resolved.
     */
    fun locationAsJsonPath(): String? = location?.let { path ->
        buildString {
            append("$")
            path.forEach { segment ->
                when (segment) {
                    is JsonNull -> append("[*]")
                    is JsonPrimitive -> {
                        val intIndex = segment.intOrNull
                        if (!segment.isString && intIndex != null) append("[$intIndex]")
                        else append(".").append(segment.content)
                    }
                    else -> append(".").append(segment.toString())
                }
            }
        }
    }

    // Secondary constructor used by parser
    constructor(jsonArray: JsonArray, encoded: String) : this(
        salt = jsonArray[0].jsonPrimitive.content,
        name = if (jsonArray.size == 3) jsonArray[1].jsonPrimitive.content else null,
        value = if (jsonArray.size == 3) jsonArray[2] else jsonArray[1],
        encoded = encoded
    )
}
