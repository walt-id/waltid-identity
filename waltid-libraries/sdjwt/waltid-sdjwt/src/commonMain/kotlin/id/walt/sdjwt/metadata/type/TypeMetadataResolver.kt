package id.walt.sdjwt.metadata.type

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64

enum class TypeMetadataFailure { INVALID_METADATA, INTEGRITY_MISMATCH, UNAVAILABLE, CYCLE, LIMIT_EXCEEDED }

class TypeMetadataException(
    val reason: TypeMetadataFailure,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/** A well-formed reference extracted from a credential's authenticated payload. */
class TypeMetadataReference private constructor(val vct: String, val integrity: String?) {
    companion object {
        fun fromCredential(payload: JsonObject): TypeMetadataReference = TypeMetadataReference(
            payload.string("vct") ?: fail(TypeMetadataFailure.INVALID_METADATA, "Missing credential vct"),
            payload.string("vct#integrity"),
        )
    }
}

/** An original document. Its bytes, rather than a reserialized model, are the integrity input. */
class TypeMetadataDocument internal constructor(val vct: String, bytes: ByteArray, val metadata: JsonObject) {
    private val content = bytes.copyOf()
    fun bytes(): ByteArray = content.copyOf()
}

/** Only a completed resolution can produce this view. Documents are ordered leaf to root. */
class ResolvedTypeMetadata internal constructor(
    val documents: List<TypeMetadataDocument>,
    val effective: JsonObject,
) {
    val display: List<ResolvedMetadataDisplay> = (effective["display"] as? JsonArray)
        ?.map { ResolvedMetadataDisplay(it.jsonObject) }.orEmpty()
    val claims: List<ResolvedMetadataClaim> = (effective["claims"] as? JsonArray)
        ?.map { ResolvedMetadataClaim(it.jsonObject) }.orEmpty()
    val types: Set<String> get() = documents.mapTo(linkedSetOf()) { it.vct }
    val schemas: List<TypeMetadataDocument> get() = documents.filter {
        "schema" in it.metadata || "schema_uri" in it.metadata
    }

    /** Persist original bytes; readers must resolve and check the credential's pins again. */
    fun snapshot(): JsonObject = buildJsonObject {
        put("version", 1)
        put("documents", JsonObject(documents.associate { it.vct to JsonPrimitive(Base64.Default.encode(it.bytes())) }))
    }
}

/** Pure bounded chain processing. The loader owns transport policy and can use pinned cached bytes. */
class TypeMetadataResolver(
    private val load: suspend (String) -> ByteArray,
    private val maxDepth: Int = 8,
    private val maxDocumentBytes: Int = 262_144,
    private val maxTotalBytes: Int = 2_097_152,
) {
    init {
        require(maxDepth > 0 && maxDocumentBytes > 0 && maxTotalBytes > 0)
    }

    suspend fun resolve(vct: String, integrity: String? = null, requireParentIntegrity: Boolean = false): ResolvedTypeMetadata {
        val documents = mutableListOf<TypeMetadataDocument>()
        val seen = mutableSetOf<String>()
        var uri: String? = vct
        var pin = integrity
        var total = 0L
        while (uri != null) {
            val current = uri
            if (!seen.add(current)) fail(TypeMetadataFailure.CYCLE, "Circular type metadata inheritance")
            if (documents.size == maxDepth) fail(TypeMetadataFailure.LIMIT_EXCEEDED, "Type metadata chain is too deep")
            if (current.isBlank()) fail(TypeMetadataFailure.INVALID_METADATA, "Empty type identifier")
            val bytes = try {
                load(current)
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: TypeMetadataException) {
                throw cause
            } catch (cause: Exception) {
                throw TypeMetadataException(TypeMetadataFailure.UNAVAILABLE, "Type metadata unavailable", cause)
            }
            total += bytes.size
            if (bytes.size > maxDocumentBytes || total > maxTotalBytes) {
                fail(TypeMetadataFailure.LIMIT_EXCEEDED, "Type metadata byte limit exceeded")
            }
            pin?.let { MetadataIntegrity.verify(bytes, it) }
            val metadata = try {
                Json.parseToJsonElement(bytes.decodeToString(throwOnInvalidSequence = true)) as? JsonObject
                    ?: fail(TypeMetadataFailure.INVALID_METADATA, "Type metadata must be an object")
            } catch (cause: TypeMetadataException) {
                throw cause
            } catch (cause: Exception) {
                throw TypeMetadataException(TypeMetadataFailure.INVALID_METADATA, "Invalid type metadata JSON", cause)
            }
            if (metadata.string("vct") != current) fail(TypeMetadataFailure.INVALID_METADATA, "Type metadata identity mismatch")
            validate(metadata)
            documents += TypeMetadataDocument(current, bytes, metadata)
            uri = metadata.string("extends")
            pin = metadata.string("extends#integrity")
            if (pin != null && uri == null) fail(TypeMetadataFailure.INVALID_METADATA, "Parent integrity without a parent")
            if (requireParentIntegrity && uri != null && pin == null) {
                fail(TypeMetadataFailure.INVALID_METADATA, "Parent integrity is required")
            }
        }
        val effective = documents.asReversed().fold(JsonObject(emptyMap())) { inherited, document -> merge(inherited, document.metadata) }
        return ResolvedTypeMetadata(documents.toList(), effective)
    }

    companion object {
        /** Restore bytes only; this does not trust persisted derived fields or bypass resolution. */
        fun snapshotDocuments(snapshot: JsonObject): Map<String, ByteArray> {
            if (snapshot["version"] != JsonPrimitive(1)) fail(TypeMetadataFailure.INVALID_METADATA, "Unsupported metadata snapshot")
            val documents = snapshot["documents"] as? JsonObject ?: fail(TypeMetadataFailure.INVALID_METADATA, "Invalid metadata snapshot")
            if (documents.size > 8) fail(TypeMetadataFailure.LIMIT_EXCEEDED, "Metadata snapshot is too large")
            var total = 0L
            return documents.mapValues { (_, value) ->
                val encoded = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
                    ?: fail(TypeMetadataFailure.INVALID_METADATA, "Invalid metadata snapshot bytes")
                if (encoded.length > 349_528) fail(TypeMetadataFailure.LIMIT_EXCEEDED, "Metadata snapshot document is too large")
                val decoded = try {
                    Base64.Default.decode(encoded)
                } catch (cause: IllegalArgumentException) {
                    throw TypeMetadataException(TypeMetadataFailure.INVALID_METADATA, "Invalid metadata snapshot bytes", cause)
                }
                decoded.also {
                    total += it.size
                    if (total > 2_097_152) fail(TypeMetadataFailure.LIMIT_EXCEEDED, "Metadata snapshot is too large")
                }
            }
        }
    }
}

private fun JsonObject.string(name: String): String? {
    if (name !in this) return null
    return (this[name] as? JsonPrimitive)?.takeIf { it.isString && it.content.isNotBlank() }?.content
        ?: fail(TypeMetadataFailure.INVALID_METADATA, "Invalid metadata property '$name'")
}

private fun validate(metadata: JsonObject) {
    for (name in listOf("vct", "extends", "extends#integrity", "schema_uri", "schema_uri#integrity")) metadata.string(name)
    if ("schema" in metadata && (metadata["schema"] !is JsonObject || "schema_uri" in metadata)) {
        fail(TypeMetadataFailure.INVALID_METADATA, "Invalid schema declaration")
    }
    if ("schema_uri#integrity" in metadata && "schema_uri" !in metadata) {
        fail(TypeMetadataFailure.INVALID_METADATA, "Schema integrity without URI")
    }
    metadata["claims"]?.let { claims ->
        val paths = mutableSetOf<JsonArray>()
        (claims as? JsonArray ?: fail(TypeMetadataFailure.INVALID_METADATA, "Claims must be an array")).forEach { value ->
            val claim = value as? JsonObject ?: fail(TypeMetadataFailure.INVALID_METADATA, "Invalid claim metadata")
            val path = claim["path"] as? JsonArray ?: fail(TypeMetadataFailure.INVALID_METADATA, "Missing claim path")
            if (path.isEmpty() || !paths.add(path) || path.any { segment ->
                segment != JsonNull && (segment !is JsonPrimitive || (!segment.isString && (segment.intOrNull == null || segment.intOrNull!! < 0)))
            }) fail(TypeMetadataFailure.INVALID_METADATA, "Invalid or duplicate claim path")
            claim["mandatory"]?.let {
                if (it !is JsonPrimitive || it.isString || it.booleanOrNull == null) {
                    fail(TypeMetadataFailure.INVALID_METADATA, "Invalid mandatory restriction")
                }
            }
            claim["sd"]?.let {
                if (it !in listOf("allowed", "always", "never").map(::JsonPrimitive)) {
                    fail(TypeMetadataFailure.INVALID_METADATA, "Invalid selective disclosure restriction")
                }
            }
            normalizedDisplay(claim["display"], claimLabels = true)
        }
    }
    normalizedDisplay(metadata["display"])
}

private fun normalizedDisplay(value: JsonElement?, claimLabels: Boolean = false): JsonArray? = value?.let {
    JsonArray((it as? JsonArray ?: fail(TypeMetadataFailure.INVALID_METADATA, "Display must be an array")).map { entry ->
        val obj = entry as? JsonObject ?: fail(TypeMetadataFailure.INVALID_METADATA, "Invalid display entry")
        val locale = obj.string("locale") ?: obj.string("lang") ?: fail(TypeMetadataFailure.INVALID_METADATA, "Display locale missing")
        obj.string(if (claimLabels) "label" else "name") ?: fail(TypeMetadataFailure.INVALID_METADATA, "Display label missing")
        JsonObject(obj.toMutableMap().apply {
            remove("lang")
            put("locale", JsonPrimitive(locale))
        })
    })
}

private fun merge(parent: JsonObject, child: JsonObject): JsonObject {
    val result = parent.toMutableMap().apply { putAll(child) }
    normalizedDisplay(child["display"])?.let { result["display"] = it }
    val claims = linkedMapOf<JsonElement, JsonObject>()
    for (metadata in listOf(parent, child)) (metadata["claims"] as? JsonArray)?.forEach { value ->
        val claim = value.jsonObject
        val previous = claims[claim.getValue("path")]
        if (previous != null) {
            val oldSd = previous["sd"]
            if ((oldSd == JsonPrimitive("always") || oldSd == JsonPrimitive("never")) && claim["sd"] != null && claim["sd"] != oldSd) {
                fail(TypeMetadataFailure.INVALID_METADATA, "Cannot weaken inherited selective disclosure restriction")
            }
            if (previous["mandatory"] == JsonPrimitive(true) && claim["mandatory"] == JsonPrimitive(false)) {
                fail(TypeMetadataFailure.INVALID_METADATA, "Cannot weaken inherited mandatory restriction")
            }
        }
        claims[claim.getValue("path")] = JsonObject(previous.orEmpty() + claim +
            (normalizedDisplay(claim["display"], claimLabels = true)?.let { mapOf("display" to it) } ?: emptyMap()))
    }
    if (claims.isNotEmpty() || "claims" in child) result["claims"] = JsonArray(claims.values.toList())
    listOf("extends", "extends#integrity", "schema", "schema_uri", "schema_uri#integrity").forEach(result::remove)
    return JsonObject(result)
}

private fun fail(reason: TypeMetadataFailure, message: String): Nothing = throw TypeMetadataException(reason, message)
