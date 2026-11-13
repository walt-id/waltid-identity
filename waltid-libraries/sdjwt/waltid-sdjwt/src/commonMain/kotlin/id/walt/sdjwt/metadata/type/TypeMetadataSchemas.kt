package id.walt.sdjwt.metadata.type

import com.eygraber.uri.Uri
import io.github.optimumcode.json.schema.JsonSchema
import io.github.optimumcode.json.schema.JsonSchemaLoader
import io.github.optimumcode.json.schema.SchemaType
import kotlinx.serialization.json.*
import kotlinx.coroutines.CancellationException

/** Every ancestor schema remains an independent constraint with its own retrieval URI. */
internal object TypeMetadataSchemas {
    suspend fun compile(resolved: ResolvedTypeMetadata, load: suspend (String) -> ByteArray): List<JsonSchema> = try {
        compileSchemas(resolved, load)
    } catch (cause: CancellationException) { throw cause
    } catch (cause: TypeMetadataException) { throw cause
    } catch (cause: Exception) {
        throw TypeMetadataException(TypeMetadataFailure.INVALID_METADATA, "Type schema preparation failed", cause)
    }

    private suspend fun compileSchemas(resolved: ResolvedTypeMetadata, load: suspend (String) -> ByteArray): List<JsonSchema> {
        if (resolved.schemas.isEmpty()) return emptyList()
        val loader = JsonSchemaLoader.create()
        val resources = mutableSetOf<String>()
        val references = linkedSetOf<String>()

        fun documentUri(value: String) = value.substringBefore('#')

        // Only schema-valued keywords introduce schema locations. Examples, const and enum contain
        // instance data, even when their objects have keys named $id or $ref.
        fun normalize(value: JsonElement, base: String, root: Boolean = false): JsonElement {
            val schema = value as? JsonObject ?: return value
            val current = schema["\$id"]?.jsonPrimitive?.content?.let { resolveSchemaUri(base, it) } ?: base
            resources += documentUri(current)
            return JsonObject(schema.mapValues { (keyword, child) ->
                when {
                    keyword == "\$id" -> JsonPrimitive(current)
                    keyword in REFERENCE_KEYWORDS -> {
                        val reference = child.jsonPrimitive.content
                        val target = resolveSchemaUri(current, reference)
                        references += documentUri(target)
                        child
                    }
                    keyword in SCHEMA_MAP_KEYWORDS && child is JsonObject ->
                        JsonObject(child.mapValues { normalize(it.value, current) })
                    keyword in SCHEMA_ARRAY_KEYWORDS && child is JsonArray ->
                        JsonArray(child.map { normalize(it, current) })
                    keyword in SCHEMA_KEYWORDS -> when (child) {
                        is JsonArray -> JsonArray(child.map { normalize(it, current) }) // legacy tuple items
                        else -> normalize(child, current)
                    }
                    else -> child
                }
            } + if (root && "\$id" !in schema) mapOf("\$id" to JsonPrimitive(current)) else emptyMap())
        }

        fun register(uri: String, schema: JsonElement) {
            resources += documentUri(uri)
            loader.register(normalize(schema, uri, root = true), Uri.parse(uri), SchemaType.DRAFT_2020_12)
        }

        // Register all roots and embedded resources before deciding which references need a GET.
        val roots = resolved.schemas.map { document ->
            val metadata = document.metadata
            val uri = metadata["schema_uri"]?.jsonPrimitive?.content ?: document.vct
            val schema = metadata["schema"] ?: load(uri).let { bytes ->
                metadata["schema_uri#integrity"]?.jsonPrimitive?.content?.let { MetadataIntegrity.verify(bytes, it) }
                Json.parseToJsonElement(bytes.decodeToString())
            }
            register(uri, schema)
            uri
        }
        while (true) {
            val missing = references.firstOrNull { it !in resources } ?: break
            register(missing, Json.parseToJsonElement(load(missing).decodeToString()))
        }
        // Compile a reference to the registered root: compiling its definition again loses retrieval
        // context (and can register the same embedded resources twice). The validator owns resolution.
        return roots.map { uri ->
            loader.fromJsonElement(buildJsonObject { put("\$ref", uri) }, SchemaType.DRAFT_2020_12)
        }
    }

    private val REFERENCE_KEYWORDS = setOf("\$ref", "\$dynamicRef", "\$recursiveRef")
    private val SCHEMA_MAP_KEYWORDS = setOf("\$defs", "definitions", "properties", "patternProperties", "dependentSchemas", "dependencies")
    private val SCHEMA_ARRAY_KEYWORDS = setOf("allOf", "anyOf", "oneOf", "prefixItems")
    private val SCHEMA_KEYWORDS = setOf(
        "items", "additionalItems", "additionalProperties", "contains", "propertyNames",
        "not", "if", "then", "else", "unevaluatedItems", "unevaluatedProperties", "contentSchema",
    )
}

/** URI resolution for schema resources, including fragment-only references on opaque URNs. */
private fun resolveSchemaUri(base: String, reference: String): String {
    val target = Uri.parse(reference)
    if (target.isAbsolute) return target.toString()
    val current = Uri.parse(base)
    val builder = current.buildUpon().encodedFragment(target.encodedFragment)
    if (reference.isEmpty() || reference.startsWith('#')) return builder.build().toString()
    require(!current.isOpaque) { "A relative schema document requires a hierarchical base URI" }
    if (target.encodedAuthority != null) return target.buildUpon().scheme(current.scheme).build().toString()
    val path = target.encodedPath.orEmpty()
    if (path.isEmpty()) {
        target.encodedQuery?.let { builder.encodedQuery(it) }
    } else {
        val merged = if (path.startsWith('/')) path else current.encodedPath.orEmpty().substringBeforeLast('/', "") + "/" + path
        val segments = mutableListOf<String>()
        for (segment in merged.split('/')) when (segment) {
            "." -> Unit
            ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
            else -> segments += segment
        }
        var normalized = segments.joinToString("/")
        if (merged.endsWith("/.") || merged.endsWith("/..")) normalized += "/"
        builder.encodedPath(normalized).encodedQuery(target.encodedQuery)
    }
    return builder.build().toString()
}
