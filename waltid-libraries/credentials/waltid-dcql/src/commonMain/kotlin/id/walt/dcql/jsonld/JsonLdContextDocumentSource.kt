package id.walt.dcql.jsonld

import kotlinx.serialization.json.JsonObject

/**
 * Already-loaded JSON-LD context documents, keyed by the context URL without a fragment.
 *
 * Remote loading stays outside this type so DCQL matching can stay synchronous.
 */
fun interface JsonLdContextDocumentSource {
    fun resolve(url: String): JsonObject?

    companion object {
        val bundled: JsonLdContextDocumentSource = BundledW3cContexts.source
    }
}

class MapJsonLdContextDocuments(
    documents: Map<String, JsonObject>,
) : JsonLdContextDocumentSource {
    private val documents = documents.mapKeys { (url, _) -> url.substringBefore('#') }

    override fun resolve(url: String): JsonObject? = documents[url.substringBefore('#')]
}

class LayeredJsonLdContextDocuments(
    private val layers: List<JsonLdContextDocumentSource>,
) : JsonLdContextDocumentSource {
    override fun resolve(url: String): JsonObject? = layers.firstNotNullOfOrNull { it.resolve(url) }
}
