package id.walt.sdjwt.metadata.type

import kotlinx.serialization.json.*

/** Reproducible hosted representation. External documents are always hashed as retrieved. */
class PublishedTypeMetadata private constructor(val metadata: JsonObject) {
    private val content = metadata.toString().encodeToByteArray()
    val vct: String = metadata.getValue("vct").jsonPrimitive.content
    val integrity: String = MetadataIntegrity.sha256(content)
    fun bytes(): ByteArray = content.copyOf()

    companion object {
        fun create(metadata: JsonObject): PublishedTypeMetadata {
            require((metadata["vct"] as? JsonPrimitive)?.let { it.isString && it.content.isNotBlank() } == true) { "Publication requires a VCT string" }
            return PublishedTypeMetadata(Json.parseToJsonElement(metadata.toString()).jsonObject)
        }

    }
}

/** Prepares configured hosted types together, filling absent parent pins and validating supplied ones. */
class TypeMetadataPublisher(private val loadExternal: suspend (String) -> ByteArray) {
    suspend fun publish(configured: Map<String, JsonObject>): Map<String, PublishedTypeMetadata> {
        val published = mutableMapOf<String, PublishedTypeMetadata>()
        val visiting = mutableSetOf<String>()
        val external = mutableMapOf<String, ByteArray>()
        suspend fun prepare(vct: String, depth: Int): ByteArray {
            published[vct]?.let { return it.bytes() }
            external[vct]?.let { return it }
            if (depth >= 8) throw TypeMetadataException(TypeMetadataFailure.LIMIT_EXCEEDED, "Published type hierarchy is too deep")
            if (!visiting.add(vct)) throw TypeMetadataException(TypeMetadataFailure.CYCLE, "Circular configured type hierarchy")
            val source = configured[vct]
            val bytes = if (source == null) loadExternal(vct).also { external[vct] = it } else {
                require(source["vct"] == JsonPrimitive(vct)) { "Configured type identity mismatch" }
                val metadata = source.toMutableMap()
                source["extends"]?.let { parent ->
                    val parentVct = parent.jsonPrimitive.takeIf { it.isString }?.content ?: error("Invalid parent type")
                    val parentBytes = prepare(parentVct, depth + 1)
                    val configuredPin = source["extends#integrity"]?.jsonPrimitive?.content
                    if (configuredPin != null) MetadataIntegrity.verify(parentBytes, configuredPin)
                    else metadata["extends#integrity"] = JsonPrimitive(MetadataIntegrity.sha256(parentBytes))
                }
                PublishedTypeMetadata.create(JsonObject(metadata)).also { published[vct] = it }.bytes()
            }
            visiting.remove(vct)
            return bytes
        }
        configured.keys.forEach { prepare(it, 0) }
        val loader: suspend (String) -> ByteArray = { vct -> published[vct]?.bytes() ?: external[vct] ?: loadExternal(vct).also { external[vct] = it } }
        for ((vct, metadata) in published) TypeMetadataResolver(loader).resolve(vct, metadata.integrity)
        return published.toMap()
    }
}
