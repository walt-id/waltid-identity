package id.walt.ades.jades

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

enum class JadesSerialization { COMPACT, JSON_FLATTENED, JSON_GENERAL }

/** How unsigned components are incorporated in etsiU (TS 119 182-1 5.3.1). */
enum class EtsiUIncorporation { BASE64URL, CLEAR }

/**
 * One element of the etsiU array: a JSON object with a single member ([name] -> [value]), either in clear or as its
 * base64url encoding ([encoded], kept verbatim because it is the time-stamped representation).
 */
class EtsiUComponent private constructor(val name: String, val value: JsonElement, val encoded: String?) {
    val isBase64Url: Boolean get() = encoded != null

    /** The array element as it appears in etsiU. */
    val element: JsonElement get() = encoded?.let(::JsonPrimitive) ?: buildJsonObject { put(name, value) }

    /** Bytes this component contributes to arcTst message imprints, or null if [canonAlg] is not supported. */
    fun imprintBytes(canonAlg: String?): ByteArray? = when {
        encoded != null -> encoded.encodeToByteArray()
        canonAlg == JadesHeaders.CANON_JCS -> JadesEncoding.canonicalize(element).encodeToByteArray()
        canonAlg == null -> JadesEncoding.encode(element).encodeToByteArray()
        else -> null
    }

    override fun toString() = "EtsiUComponent($name${if (isBase64Url) ", base64url" else ""})"

    companion object {
        fun create(name: String, value: JsonElement, incorporation: EtsiUIncorporation): EtsiUComponent = when (incorporation) {
            EtsiUIncorporation.BASE64URL -> EtsiUComponent(name, value, JadesEncoding.b64url(JadesEncoding.encode(buildJsonObject { put(name, value) })))
            EtsiUIncorporation.CLEAR -> EtsiUComponent(name, value, null)
        }

        fun parse(element: JsonElement): EtsiUComponent {
            val (obj, encoded) = when {
                element is JsonPrimitive && element.isString ->
                    JadesEncoding.parseObject(JadesEncoding.fromB64url(element.content).decodeToString()) to element.content

                element is JsonObject -> element to null
                else -> throw IllegalArgumentException("etsiU element must be a JSON object or a base64url string")
            }
            require(obj.size == 1) { "etsiU element must contain exactly one member, found ${obj.keys}" }
            val (name, value) = obj.entries.single()
            return EtsiUComponent(name, value, encoded)
        }
    }
}

/** One signature of a JWS: protected header, unprotected header (etsiU) and signature value, with their encodings preserved. */
class JadesSignature(
    /** BASE64URL(UTF8(JWS Protected Header)) exactly as serialized. */
    val protectedB64: String,
    /** BASE64URL(JWS Signature) exactly as serialized. */
    val signatureB64: String,
    val etsiU: List<EtsiUComponent> = emptyList(),
    /** Unprotected header members other than etsiU (discouraged by TS 119 182-1 clause 4). */
    val otherUnprotected: JsonObject = JsonObject(emptyMap()),
) {
    val protectedHeader: JsonObject by lazy { JadesEncoding.parseObject(JadesEncoding.fromB64url(protectedB64).decodeToString()) }
    val signatureValue: ByteArray get() = JadesEncoding.fromB64url(signatureB64)

    /** b64 header parameter (RFC 7797); true when absent. */
    val base64UrlPayload: Boolean get() = (protectedHeader[JadesHeaders.B64] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: true

    val hasUnprotectedHeader: Boolean get() = etsiU.isNotEmpty() || otherUnprotected.isNotEmpty()

    fun components(name: String): List<EtsiUComponent> = etsiU.filter { it.name == name }

    fun withEtsiU(items: List<EtsiUComponent>) = JadesSignature(protectedB64, signatureB64, items, otherUnprotected)

    internal fun headerJson(): JsonObject? = if (!hasUnprotectedHeader) null else buildJsonObject {
        otherUnprotected.forEach { (k, v) -> put(k, v) }
        if (etsiU.isNotEmpty()) put(JadesHeaders.ETSI_U, JsonArray(etsiU.map { it.element }))
    }

    internal fun jsonMembers(): Map<String, JsonElement> = buildMap {
        put("protected", JsonPrimitive(protectedB64))
        headerJson()?.let { put("header", it) }
        put("signature", JsonPrimitive(signatureB64))
    }
}

/**
 * A parsed JWS / JAdES document. [payloadSegment] is the JWS Payload as it appears in the serialization (base64url
 * encoded, or the raw payload when b64=false); null or empty when detached.
 */
class JadesDocument(
    val payloadSegment: String?,
    val signatures: List<JadesSignature>,
    val serialization: JadesSerialization,
) {
    init {
        require(signatures.isNotEmpty()) { "A JWS has at least one signature" }
        require(serialization == JadesSerialization.JSON_GENERAL || signatures.size == 1) { "$serialization carries exactly one signature" }
    }

    val isDetached: Boolean get() = payloadSegment.isNullOrEmpty()

    fun withSignature(index: Int, signature: JadesSignature) =
        JadesDocument(payloadSegment, signatures.toMutableList().also { it[index] = signature }, serialization)

    fun withSerialization(target: JadesSerialization) = JadesDocument(payloadSegment, signatures, target)

    /** Serializes; a signature carrying an unprotected header cannot use compact serialization and falls back to flattened JSON. */
    fun serialize(): String {
        val effective = if (serialization == JadesSerialization.COMPACT && signatures.single().hasUnprotectedHeader) JadesSerialization.JSON_FLATTENED else serialization
        return when (effective) {
            JadesSerialization.COMPACT -> {
                val s = signatures.single()
                "${s.protectedB64}.${payloadSegment ?: ""}.${s.signatureB64}"
            }

            JadesSerialization.JSON_FLATTENED -> JadesEncoding.encode(buildJsonObject {
                payloadSegment?.let { put("payload", JsonPrimitive(it)) }
                signatures.single().jsonMembers().forEach { (k, v) -> put(k, v) }
            })

            JadesSerialization.JSON_GENERAL -> JadesEncoding.encode(buildJsonObject {
                payloadSegment?.let { put("payload", JsonPrimitive(it)) }
                put("signatures", JsonArray(signatures.map { JsonObject(it.jsonMembers()) }))
            })
        }
    }

    override fun toString(): String = serialize()

    companion object {
        fun parse(text: String): JadesDocument {
            val trimmed = text.trim()
            if (trimmed.startsWith("{")) return parseJson(JadesEncoding.parseObject(trimmed))
            val first = trimmed.indexOf('.')
            val last = trimmed.lastIndexOf('.')
            require(first > 0 && last > first) { "Not a JWS compact serialization" }
            return JadesDocument(
                trimmed.substring(first + 1, last),
                listOf(JadesSignature(trimmed.substring(0, first), trimmed.substring(last + 1))),
                JadesSerialization.COMPACT,
            )
        }

        fun parse(element: JsonElement): JadesDocument = when {
            element is JsonPrimitive && element.isString -> parse(element.content)
            element is JsonObject -> parseJson(element)
            else -> throw IllegalArgumentException("Not a JWS")
        }

        private fun parseJson(obj: JsonObject): JadesDocument {
            val payload = obj["payload"]?.jsonPrimitive?.content
            val sigs = obj["signatures"]
            return if (sigs is JsonArray) {
                JadesDocument(payload, sigs.map { parseSignature(it as JsonObject) }, JadesSerialization.JSON_GENERAL)
            } else JadesDocument(payload, listOf(parseSignature(obj)), JadesSerialization.JSON_FLATTENED)
        }

        private fun parseSignature(obj: JsonObject): JadesSignature {
            val protected = obj["protected"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("JAdES signatures require a JWS Protected Header")
            val signature = obj["signature"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing signature member")
            val header = obj["header"] as? JsonObject ?: JsonObject(emptyMap())
            val etsiU = when (val e = header[JadesHeaders.ETSI_U]) {
                null -> emptyList()
                is JsonArray -> e.map(EtsiUComponent::parse)
                else -> throw IllegalArgumentException("etsiU must be a JSON array")
            }
            return JadesSignature(protected, signature, etsiU, JsonObject(header - JadesHeaders.ETSI_U))
        }
    }
}
