@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package id.walt.dcql.jsonld

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.concurrent.atomics.AtomicReference
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Expands W3C `type` values the way OpenID4VP 1.0 Appendix B.1.1 requires: after applying
 * `@context`, using JSON-LD IRI expansion. Claims stay in compact form.
 *
 * Only the active context at the credential node is used. Type-scoped contexts inside a term
 * definition apply to that term's properties, not to the `type` values themselves.
 * `@import` and document-base resolution are not applied. A context document with no `@context`
 * is ignored. An unavailable context document leaves unprotected expansions that it could have
 * changed unpublished, including a later alias of an uncertain prefix or term, so those inferred
 * IRIs do not match.
 */
object W3cTypeExpander {
    private val log = KotlinLogging.logger {}
    private val processedContexts = AtomicReference(emptyMap<ContextCacheKey, JsonLdActiveContext>())

    fun expandedTypes(
        data: JsonObject,
        documents: JsonLdContextDocumentSource = JsonLdContextDocumentSource.bundled,
    ): Set<String> {
        val node = w3cCredentialNode(data)
        val types = stringArray(node["type"])
        if (types.isEmpty()) return emptySet()
        val active = activeContext(node["@context"], documents)
        return types.map { publishExpansion(it, active) }.toSet()
    }

    private fun publishExpansion(value: String, active: JsonLdActiveContext): String {
        if (active.sawUnresolvedContext && expansionIsUncertain(value, active)) return value
        return expandIri(value, active, vocabRelative = true)
    }

    private fun expansionIsUncertain(value: String, active: JsonLdActiveContext): Boolean {
        if (value.startsWith("@")) return false
        val colon = value.indexOf(':')
        if (colon > 0) {
            val prefix = value.substring(0, colon)
            val suffix = value.substring(colon + 1)
            if (prefix == "_" || suffix.startsWith("//")) return false
            return prefix !in active.terms || prefix in active.uncertainTerms
        }
        if (value in active.terms) return value in active.uncertainTerms
        return true
    }

    private fun activeContext(
        context: JsonElement?,
        documents: JsonLdContextDocumentSource,
    ): JsonLdActiveContext {
        if (context == null) return JsonLdActiveContext()
        val key = ContextCacheKey(documents, context)
        processedContexts.load()[key]?.let { return it }
        val processed = processContext(JsonLdActiveContext(), context, documents, mutableSetOf())
        val frozen = JsonLdActiveContext(
            terms = processed.terms.toMap(),
            vocab = processed.vocab,
            uncertainTerms = processed.uncertainTerms.toSet(),
            sawUnresolvedContext = processed.sawUnresolvedContext,
            vocabUncertain = processed.vocabUncertain,
        )
        return remember(key, frozen)
    }

    private fun remember(key: ContextCacheKey, value: JsonLdActiveContext): JsonLdActiveContext {
        while (true) {
            val current = processedContexts.load()
            current[key]?.let { return it }
            val retained = if (current.size < CONTEXT_CACHE_LIMIT) current
            else current.entries.drop(current.size - CONTEXT_CACHE_LIMIT + 1).associate { it.key to it.value }
            if (processedContexts.compareAndSet(current, retained + (key to value))) return value
        }
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
        null -> active
        is JsonNull -> if (active.hasProtectedTerms) {
            log.debug { "Ignoring JSON-LD null context because protected terms are active" }
            active
        } else {
            JsonLdActiveContext()
        }
        is JsonArray -> context.fold(active) { current, element ->
            processContext(current, element, documents, resolving)
        }
        is JsonPrimitive -> {
            val url = context.contentOrNull?.substringBefore('#')
            if (url.isNullOrBlank() || !resolving.add(url)) active
            else try {
                val document = documents.resolve(url)
                val nested = document?.get("@context")
                when {
                    document == null -> {
                        log.debug { "JSON-LD context document was not available: $url" }
                        active.noteUnresolvedContext()
                        active
                    }
                    nested == null -> {
                        log.debug { "JSON-LD context document has no @context: $url" }
                        active
                    }
                    else -> processContext(active, nested, documents, resolving)
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
        val session = TermDefinitionSession(
            active = active,
            local = context,
            protectedTerms = (context["@protected"] as? JsonPrimitive)?.booleanOrNull == true,
        )
        when (val vocab = context["@vocab"]) {
            null -> Unit
            is JsonNull -> {
                session.active.vocab = null
                session.active.vocabUncertain = false
            }
            is JsonPrimitive -> if (vocab.isString) {
                session.active.vocab = expandIri(vocab.content, session.active, vocabRelative = true, session)
                session.active.vocabUncertain = expansionIsUncertain(vocab.content, session.active)
            }
            else -> Unit
        }
        for (term in context.keys) {
            if (!term.startsWith("@")) defineTerm(session, term)
        }
        return session.active
    }

    private fun defineTerm(session: TermDefinitionSession, term: String) {
        when (session.defined[term]) {
            true -> return
            false -> {
                session.defined[term] = true
                return
            }
            null -> Unit
        }
        val value = session.local[term] ?: return
        if (value is JsonNull) {
            session.defined[term] = true
            val existing = session.active.terms[term]
            if (existing?.protected != true) {
                session.active.terms.remove(term)
                session.active.uncertainTerms.remove(term)
            }
            return
        }
        session.defined[term] = false
        val defined = createTerm(session, term, value) ?: run {
            session.defined[term] = true
            return
        }
        session.defined[term] = true
        val existing = session.active.terms[term]
        if (existing?.protected == true && (existing.iri != defined.iri || existing.prefix != defined.prefix)) {
            return
        }
        val uncertain = definedValueIsUncertain(term, value, session.active)
        session.active.terms[term] = defined.copy(protected = existing?.protected == true || defined.protected)
        if (uncertain) {
            session.active.uncertainTerms.add(term)
        } else {
            session.active.uncertainTerms.remove(term)
        }
    }

    /**
     * A definition whose value names the term being created expands through `@vocab`, not through
     * the mapping this call is about to install. That vocabulary may itself be uncertain.
     */
    private fun definedValueIsUncertain(term: String, value: JsonElement, active: JsonLdActiveContext): Boolean = when (value) {
        is JsonPrimitive -> value.isString && !value.content.startsWith("@") && iriSourceIsUncertain(term, value.content, active)
        is JsonObject -> when (val id = value["@id"]) {
            null -> active.vocabUncertain
            is JsonPrimitive -> id.isString && !id.content.startsWith("@") && iriSourceIsUncertain(term, id.content, active)
            else -> false
        }
        else -> false
    }

    private fun iriSourceIsUncertain(defining: String, value: String, active: JsonLdActiveContext): Boolean {
        if (value.startsWith("@")) return false
        val colon = value.indexOf(':')
        if (colon > 0) {
            val prefix = value.substring(0, colon)
            val suffix = value.substring(colon + 1)
            if (prefix == "_" || suffix.startsWith("//")) return false
            if (prefix == defining) return active.vocabUncertain
            return prefix in active.uncertainTerms
        }
        if (value == defining || value !in active.terms) return active.vocabUncertain
        return value in active.uncertainTerms
    }

    private fun createTerm(session: TermDefinitionSession, term: String, value: JsonElement): JsonLdTerm? = when (value) {
        is JsonPrimitive -> if (!value.isString) null else termFromIri(
            iri = if (value.content.startsWith("@")) value.content
            else expandIri(value.content, session.active, vocabRelative = true, session),
            explicitPrefix = null,
            simpleString = true,
            protectedTerm = session.protectedTerms,
        )
        is JsonObject -> {
            val id = value["@id"] as? JsonPrimitive
            val iri = when {
                id == null -> session.active.vocab?.let { it + term }
                !id.isString -> null
                id.content.startsWith("@") -> id.content
                else -> expandIri(id.content, session.active, vocabRelative = true, session)
            }
            val explicitPrefix = (value["@prefix"] as? JsonPrimitive)?.booleanOrNull
            termFromIri(iri, explicitPrefix, simpleString = false, protectedTerm = session.protectedTerms)
        }
        else -> null
    }

    private fun termFromIri(
        iri: String?,
        explicitPrefix: Boolean?,
        simpleString: Boolean,
        protectedTerm: Boolean,
    ): JsonLdTerm? {
        if (iri == null) return null
        val prefix = explicitPrefix ?: (simpleString && !iri.startsWith("@") && iriEndsWithGenDelim(iri))
        return JsonLdTerm(iri = iri, prefix = prefix, protected = protectedTerm)
    }

    private fun expandIri(
        value: String,
        active: JsonLdActiveContext,
        vocabRelative: Boolean,
        session: TermDefinitionSession? = null,
    ): String {
        if (value.startsWith("@")) return value
        val colon = value.indexOf(':')
        if (colon > 0) {
            val prefix = value.substring(0, colon)
            val suffix = value.substring(colon + 1)
            if (prefix == "_" || suffix.startsWith("//")) return value
            if (session != null && prefix in session.local && session.defined[prefix] != true) {
                defineTerm(session, prefix)
            }
            val current = session?.active ?: active
            val prefixTerm = current.terms[prefix]
            val prefixIri = prefixTerm?.iri
            if (prefixTerm?.prefix == true && prefixIri != null && !prefixIri.startsWith("@")) {
                return prefixIri + suffix
            }
            return value
        }
        if (session != null && value in session.local && session.defined[value] != true) {
            defineTerm(session, value)
        }
        val current = session?.active ?: active
        val term = current.terms[value]
        if (term?.iri != null && (vocabRelative || !term.prefix)) return term.iri
        if (vocabRelative && current.vocab != null) return current.vocab + value
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

    private const val CONTEXT_CACHE_LIMIT = 32
}

private data class ContextCacheKey(
    val documents: JsonLdContextDocumentSource,
    val context: JsonElement,
)

private class TermDefinitionSession(
    var active: JsonLdActiveContext,
    val local: JsonObject,
    val protectedTerms: Boolean,
    val defined: MutableMap<String, Boolean> = mutableMapOf(),
)

internal class JsonLdActiveContext(
    terms: Map<String, JsonLdTerm> = emptyMap(),
    var vocab: String? = null,
    uncertainTerms: Set<String> = emptySet(),
    var sawUnresolvedContext: Boolean = false,
    var vocabUncertain: Boolean = false,
) {
    val terms: MutableMap<String, JsonLdTerm> = terms.toMutableMap()
    val uncertainTerms: MutableSet<String> = uncertainTerms.toMutableSet()
    val hasProtectedTerms: Boolean get() = terms.values.any { it.protected }

    fun noteUnresolvedContext() {
        sawUnresolvedContext = true
        if (vocab != null) vocabUncertain = true
        for ((name, term) in terms) {
            if (!term.protected) uncertainTerms.add(name)
        }
    }
}

internal data class JsonLdTerm(
    val iri: String?,
    val prefix: Boolean,
    val protected: Boolean,
)
