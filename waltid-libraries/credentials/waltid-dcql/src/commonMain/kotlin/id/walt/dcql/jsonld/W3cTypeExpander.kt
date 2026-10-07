package id.walt.dcql.jsonld

import id.walt.dcql.DcqlCredential
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Expands W3C `type` values the way OpenID4VP 1.0 Appendix B.1.1 requires: after applying
 * `@context`, using JSON-LD IRI expansion. Claims stay in compact form.
 *
 * Only the active context at the credential node is used. Type-scoped contexts inside a term
 * definition apply to that term's properties, not to the `type` values themselves.
 */
object W3cTypeExpander {
    private val log = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true }

    fun expandedTypes(
        data: JsonObject,
        documents: JsonLdContextDocumentSource = JsonLdContextDocumentSource.bundled,
    ): Set<String> {
        val node = w3cCredentialNode(data)
        val types = stringArray(node["type"])
        if (types.isEmpty()) return emptySet()
        val active = processContext(JsonLdActiveContext(), node["@context"], documents, mutableSetOf())
        return types.map { expandIri(it, active, vocabRelative = true) }.toSet()
    }

    /**
     * Context document URLs referenced by these credentials. Inline context objects are not URLs.
     * Fragment identifiers are removed because they are not part of the request target.
     */
    fun contextDocumentUrls(credentials: Iterable<DcqlCredential>): Set<String> =
        credentials.flatMap { contextDocumentUrls(w3cCredentialNode(it.data)["@context"]) }.toSet()

    fun contextDocumentUrls(context: JsonElement?): List<String> = when (context) {
        is JsonPrimitive -> context.contentOrNull?.let { listOf(it.substringBefore('#')) }.orEmpty()
        is JsonArray -> context.flatMap { element ->
            if (element is JsonPrimitive) contextDocumentUrls(element) else emptyList()
        }
        else -> emptyList()
    }

    internal fun w3cCredentialNode(data: JsonObject): JsonObject {
        if (data["type"] is JsonArray) return data
        val enveloped = data["vc"] as? JsonObject
        if (enveloped?.get("type") is JsonArray) return enveloped
        return data
    }

    private fun processContext(
        active: JsonLdActiveContext,
        context: JsonElement?,
        documents: JsonLdContextDocumentSource,
        resolving: MutableSet<String>,
    ): JsonLdActiveContext = when (context) {
        null, is JsonNull -> JsonLdActiveContext()
        is JsonArray -> context.fold(active) { current, element ->
            processContext(current, element, documents, resolving)
        }
        is JsonPrimitive -> {
            val url = context.contentOrNull?.substringBefore('#')
            if (url.isNullOrBlank() || !resolving.add(url)) active
            else try {
                val document = documents.resolve(url)
                val nested = document?.get("@context")
                if (document == null) {
                    log.debug { "JSON-LD context document was not available: $url" }
                    active
                } else if (nested == null) {
                    processContext(active, document, documents, resolving)
                } else {
                    processContext(active, nested, documents, resolving)
                }
            } finally {
                resolving.remove(url)
            }
        }
        is JsonObject -> processContextObject(active, context)
    }

    private fun processContextObject(
        active: JsonLdActiveContext,
        context: JsonObject,
    ): JsonLdActiveContext {
        var next = active
        val protectedTerms = (context["@protected"] as? JsonPrimitive)?.booleanOrNull == true
        when (val vocab = context["@vocab"]) {
            null -> Unit
            is JsonNull -> next = next.copy(vocab = null)
            is JsonPrimitive -> if (vocab.isString) {
                next = next.copy(vocab = expandIri(vocab.content, next, vocabRelative = true))
            }
            else -> Unit
        }
        for ((term, value) in context) {
            if (term.startsWith("@")) continue
            next = defineTerm(next, term, value, protectedTerms)
        }
        return next
    }

    private fun defineTerm(
        active: JsonLdActiveContext,
        term: String,
        value: JsonElement,
        protectedTerms: Boolean,
    ): JsonLdActiveContext {
        val existing = active.terms[term]
        val defined = when (value) {
            is JsonNull -> return if (existing?.protected == true) active else active.copy(terms = active.terms - term)
            is JsonPrimitive -> if (!value.isString) return active else termFromIri(
                iri = if (value.content.startsWith("@")) value.content
                else expandIri(value.content, active, vocabRelative = true),
                explicitPrefix = null,
                protectedTerm = protectedTerms,
            )
            is JsonObject -> {
                val id = value["@id"] as? JsonPrimitive
                val iri = when {
                    id == null -> active.vocab?.let { it + term }
                    !id.isString -> return active
                    id.content.startsWith("@") -> id.content
                    else -> expandIri(id.content, active, vocabRelative = true)
                }
                val explicitPrefix = (value["@prefix"] as? JsonPrimitive)?.booleanOrNull
                termFromIri(iri, explicitPrefix, protectedTerms)
            }
            else -> return active
        } ?: return active

        if (existing?.protected == true && (existing.iri != defined.iri || existing.prefix != defined.prefix)) {
            return active
        }
        val stored = defined.copy(protected = existing?.protected == true || defined.protected)
        return active.copy(terms = active.terms + (term to stored))
    }

    private fun termFromIri(iri: String?, explicitPrefix: Boolean?, protectedTerm: Boolean): JsonLdTerm? {
        if (iri == null) return null
        val prefix = explicitPrefix ?: (iri.startsWith("@").not() && iriEndsWithGenDelim(iri))
        return JsonLdTerm(iri = iri, prefix = prefix, protected = protectedTerm)
    }

    internal fun expandIri(value: String, active: JsonLdActiveContext, vocabRelative: Boolean): String {
        if (value.startsWith("@")) return value
        val colon = value.indexOf(':')
        if (colon > 0) {
            val prefix = value.substring(0, colon)
            val suffix = value.substring(colon + 1)
            if (prefix == "_" || suffix.startsWith("//")) return value
            val prefixTerm = active.terms[prefix]
            val prefixIri = prefixTerm?.iri
            if (prefixTerm?.prefix == true && prefixIri != null && !prefixIri.startsWith("@")) {
                return prefixIri + suffix
            }
        }
        val term = active.terms[value]
        if (term?.iri != null && (vocabRelative || !term.prefix)) return term.iri
        if (vocabRelative && active.vocab != null) return active.vocab + value
        return value
    }

    private fun stringArray(element: JsonElement?): List<String> =
        (element as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()

    private fun iriEndsWithGenDelim(iri: String): Boolean {
        val last = iri.lastOrNull() ?: return false
        return last in "/:?#[]@"
    }

    private val JsonPrimitive.booleanOrNull: Boolean?
        get() = if (isString) null else content.toBooleanStrictOrNull()

    /**
     * Loads context documents that are not already bundled. [fetch] returns the response body,
     * or null when the document cannot be retrieved. A failed fetch leaves those terms unexpanded.
     */
    suspend fun resolveDocuments(
        credentials: List<DcqlCredential>,
        fetch: suspend (url: String) -> String?,
    ): JsonLdContextDocumentSource {
        val loaded = linkedMapOf<String, JsonObject>()
        val pending = ArrayDeque(contextDocumentUrls(credentials))
        val seen = mutableSetOf<String>()
        while (pending.isNotEmpty() && seen.size < MAX_CONTEXT_DOCUMENTS) {
            val url = pending.removeFirst()
            if (!seen.add(url)) continue
            val document = JsonLdContextDocumentSource.bundled.resolve(url) ?: fetchDocument(url, fetch) ?: continue
            if (JsonLdContextDocumentSource.bundled.resolve(url) == null) loaded[url] = document
            pending.addAll(contextDocumentUrls(document["@context"] ?: document))
        }
        return LayeredJsonLdContextDocuments(
            listOf(JsonLdContextDocumentSource.bundled, MapJsonLdContextDocuments(loaded)),
        )
    }

    private suspend fun fetchDocument(url: String, fetch: suspend (url: String) -> String?): JsonObject? {
        val body = try {
            fetch(url)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            log.warn { "Failed to fetch JSON-LD context $url: ${error.message}" }
            return null
        } ?: return null
        return runCatching { json.parseToJsonElement(body).jsonObject }
            .onFailure { log.warn { "JSON-LD context $url was not a JSON object" } }
            .getOrNull()
    }

    private const val MAX_CONTEXT_DOCUMENTS = 32
}

internal data class JsonLdActiveContext(
    val terms: Map<String, JsonLdTerm> = emptyMap(),
    val vocab: String? = null,
)

internal data class JsonLdTerm(
    val iri: String?,
    val prefix: Boolean,
    val protected: Boolean,
)
