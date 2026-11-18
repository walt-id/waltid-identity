package id.walt.sdjwt.metadata.type

import kotlinx.serialization.json.*

/** Reproducible hosted representation. External documents are always hashed as retrieved. */
class PublishedTypeMetadata private constructor(val metadata: JsonObject, bytes: ByteArray) {
    private val content = bytes.copyOf()
    val vct: String = metadata.getValue("vct").jsonPrimitive.content
    val integrity: String = MetadataIntegrity.sha256(content)
    fun bytes(): ByteArray = content.copyOf()

    companion object {
        fun create(metadata: JsonObject): PublishedTypeMetadata {
            require((metadata["vct"] as? JsonPrimitive)?.let { it.isString && it.content.isNotBlank() } == true) { "Publication requires a VCT string" }
            val bytes = metadata.toString().encodeToByteArray()
            return PublishedTypeMetadata(Json.parseToJsonElement(bytes.decodeToString()).jsonObject, bytes)
        }

        /** External hosting owns its representation: do not reserialize it before pinning. */
        fun fromRetrieved(vct: String, bytes: ByteArray): PublishedTypeMetadata {
            val metadata = Json.parseToJsonElement(bytes.decodeToString(throwOnInvalidSequence = true)).jsonObject
            require(metadata["vct"] == JsonPrimitive(vct)) { "Retrieved type identity mismatch" }
            return PublishedTypeMetadata(metadata, bytes)
        }
    }
}

/** Prepares configured hosted types together, filling absent parent pins and validating supplied ones. */
class TypeMetadataPublisher(private val loadExternal: suspend (String) -> ByteArray) {
    /** Reconcile configured types with immutable retained publications, including historical versions. */
    suspend fun retain(
        configured: Map<String, JsonObject>,
        previous: Map<String, TypeMetadataPublication>,
    ): Map<String, TypeMetadataPublication> {
        for ((vct, source) in configured) {
            val existing = previous[vct]
            if (existing != null && existing.source != source) {
                throw TypeMetadataException(TypeMetadataFailure.INVALID_METADATA, "Published type metadata is immutable; use a new versioned VCT")
            }
        }
        val cached = mutableMapOf<String, ByteArray>()
        for (publication in previous.values) for (document in publication.resolved.documents) {
            val bytes = document.bytes()
            val existing = cached.put(document.vct, bytes)
            require(existing == null || existing.contentEquals(bytes)) { "Retained metadata has conflicting document bytes" }
        }
        val sources = previous.mapValues { it.value.source } + configured
        val load: suspend (String) -> ByteArray = { uri ->
            cached[uri] ?: loadExternal(uri).also { cached[uri] = it }
        }
        val published = TypeMetadataPublisher(load).publish(sources.mapValues { (vct, source) ->
            previous[vct]?.published?.metadata ?: source
        })
        val publicationBytes: suspend (String) -> ByteArray = { uri -> published[uri]?.bytes() ?: load(uri) }
        return published.mapValues { (vct, artifact) ->
            previous[vct]?.also { existing ->
                require(existing.published.bytes().contentEquals(artifact.bytes())) { "Retained publication bytes changed" }
            } ?: TypeMetadataPublication.create(sources.getValue(vct), artifact, publicationBytes)
        }
    }

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

/** Verified publication and original chain bytes. Storage and configuration lifetime belong to the issuer. */
class TypeMetadataPublication private constructor(
    val source: JsonObject,
    val published: PublishedTypeMetadata,
    val resolved: ResolvedTypeMetadata,
) {
    fun snapshot(): JsonObject = buildJsonObject {
        put("version", 1); put("source", source); put("chain", resolved.snapshot())
    }

    companion object {
        internal suspend fun create(
            source: JsonObject,
            published: PublishedTypeMetadata,
            load: suspend (String) -> ByteArray,
        ): TypeMetadataPublication = TypeMetadataPublication(
            source, published,
            TypeMetadataResolver({ if (it == published.vct) published.bytes() else load(it) })
                .resolve(published.vct, published.integrity),
        )

        suspend fun restore(snapshot: JsonObject): TypeMetadataPublication {
            require(snapshot["version"] == JsonPrimitive(1)) { "Unsupported publication snapshot" }
            val source = snapshot.getValue("source").jsonObject
            val vct = source.getValue("vct").jsonPrimitive.content
            val documents = TypeMetadataResolver.snapshotDocuments(snapshot.getValue("chain").jsonObject)
            val published = PublishedTypeMetadata.fromRetrieved(vct, documents.getValue(vct))
            val expected = if ("extends#integrity" in source) published.metadata else JsonObject(published.metadata - "extends#integrity")
            require(source == expected) { "Retained publication does not match its configured source" }
            return create(source, published) { documents.getValue(it) }
        }
    }
}
