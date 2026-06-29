package id.walt.etsi.validator

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

private val log = KotlinLogging.logger {}

/**
 * Resolves the JSON Schema for a credential's `vct` (SD-JWT VC) using local files, fully offline.
 *
 * Resolution chain (ETSI plugtest layout):
 *   1. `vct` value  ->  the Type Metadata document in [metadataDir] whose `vct` matches.
 *   2. that document's `schema_url`  ->  the schema file in [schemaDir] with the matching file name.
 *
 * Returns `null` when no Type Metadata or schema can be resolved for the given `vct` (the caller
 * then SKIPS the schema check rather than failing — many credentials, e.g. our own
 * `urn:etsi:eaa:credential`, have no published schema).
 *
 * Example (provided plugtest data):
 *   META-DATA/PID-eaa-Type-Metadata.json  { "vct":"urn:eudi:pid:1", "schema_url":"https://example.org/.well-known/schemas/urn-eudi-pid-1.schema.json" }
 *   SCHEMA/urn-eudi-pid-1.schema.json
 */
class SchemaResolver(
    private val schemaDir: File?,
    private val metadataDir: File?,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** vct -> Type Metadata document, built once by scanning [metadataDir]. */
    private val typeMetadataByVct: Map<String, JsonObject> by lazy {
        val dir = metadataDir
        if (dir == null || !dir.isDirectory) return@lazy emptyMap()
        dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.mapNotNull { file ->
                runCatching {
                    val obj = json.parseToJsonElement(file.readText()).jsonObject
                    val vct = obj["vct"]?.jsonPrimitive?.content
                    vct?.let { it to obj }
                }.getOrNull()
            }
            ?.toMap()
            ?: emptyMap()
    }

    /**
     * All schema files in [schemaDir], indexed by BOTH their `$id` and their file name. Indexing by
     * `$id` is necessary because a Type Metadata `schema_url` does not always match the schema's file
     * name (e.g. the provided plugtest data names a file `medical-license-qeaa-v1.schema.json` while
     * its `schema_url`/`$id` end in `medical-license-eaa-v1.schema.json`).
     */
    private val schemasByKey: Map<String, JsonObject> by lazy {
        val dir = schemaDir
        if (dir == null || !dir.isDirectory) return@lazy emptyMap()
        val map = mutableMapOf<String, JsonObject>()
        dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.forEach { file ->
            runCatching {
                val obj = json.parseToJsonElement(file.readText()).jsonObject
                map[file.name] = obj
                obj["\$id"]?.jsonPrimitive?.content?.let { id ->
                    map[id] = obj
                    map[id.substringAfterLast('/')] = obj
                }
            }
        }
        map
    }

    /**
     * Returns the JSON Schema (as a [JsonObject]) for [vct], or `null` if it cannot be resolved.
     */
    fun resolveSchemaForVct(vct: String?): JsonObject? {
        if (vct.isNullOrBlank()) return null
        if (schemaDir == null || !schemaDir.isDirectory) return null

        val metadata = typeMetadataByVct[vct]
        if (metadata == null) {
            log.debug { "No Type Metadata found for vct '$vct'; skipping schema check" }
            return null
        }

        val schemaUrl = metadata["schema_url"]?.jsonPrimitive?.content
        if (schemaUrl.isNullOrBlank()) {
            log.debug { "Type Metadata for vct '$vct' has no schema_url; skipping schema check" }
            return null
        }

        // Resolve by the full schema_url ($id match), then by the file-name component (handles both
        // the well-named case and the plugtest filename/schema_url mismatch).
        val schema = schemasByKey[schemaUrl] ?: schemasByKey[schemaUrl.substringAfterLast('/')]
        if (schema == null) {
            log.debug { "No schema matching schema_url '$schemaUrl' (vct '$vct') found in ${schemaDir.path}; skipping schema check" }
            return null
        }
        return schema
    }
}
