package id.walt.wallet2.handlers

import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.SdJwtCredential
import id.walt.credentials.formats.DigitalCredential
import id.walt.sdjwt.metadata.type.*
import id.walt.wallet2.data.StoredCredential
import id.walt.webdatafetching.BoundedDocumentSession
import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetcherId
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

internal const val TYPE_METADATA_SNAPSHOT = "sdJwtVcTypeMetadata"
private val typeMetadataFetcher by lazy { WebDataFetcher(WebDataFetcherId.WALLET2_PAYMENT_METADATA) }

/** Revalidates stored bytes against signed pins. Persisted ancestor lists are never trusted. */
internal suspend fun StoredCredential.resolveTypeMetadata(httpClient: HttpClient? = null, fetchIfMissing: Boolean = true): ResolvedTypeMetadata? {
    if (credential.format != "dc+sd-jwt") return null
    val snapshot = metadata?.get(TYPE_METADATA_SNAPSHOT) as? JsonObject
    val wire = (credential as? SdJwtCredential)?.signedWithDisclosures ?: return null
    val parsed = CredentialParser.detectAndParse(wire).second
    require(parsed.credentialData == credential.credentialData) { "Stored metadata reference differs from signed credential" }
    val reference = TypeMetadataReference.fromCredential(parsed.credentialData)
    if (!fetchIfMissing && snapshot?.get("version") != JsonPrimitive(1)) return null
    val vct = reference.vct
    val pin = reference.integrity
    if (pin != null && snapshot != null) {
        try {
            val bytes = TypeMetadataResolver.snapshotDocuments(snapshot)
            return TypeMetadataResolver({ bytes[it] ?: error("Snapshot document missing") })
                .resolve(vct, pin, requireParentIntegrity = true)
        } catch (cause: CancellationException) { throw cause
        } catch (_: Exception) { /* A cache miss cannot establish a type relationship. Fetch independently. */ }
    }
    if (!fetchIfMissing) return null
    val fetcher = httpClient?.let {
        WebDataFetcher.wrapping(it, WebDataFetcherId.WALLET2_PAYMENT_METADATA.name)
    } ?: typeMetadataFetcher
    val session = BoundedDocumentSession(fetcher)
    return session.use {
        try { TypeMetadataResolver(it::read).resolve(vct, pin)
        } catch (cause: CancellationException) { throw cause
        } catch (cause: TypeMetadataException) {
            if (cause.reason == TypeMetadataFailure.UNAVAILABLE) null else throw cause
        }
    }
}

internal suspend fun StoredCredential.withTypeMetadata(httpClient: HttpClient?): StoredCredential {
    val clean = metadata?.let { JsonObject(it - TYPE_METADATA_SNAPSHOT) }
    val candidate = copy(metadata = clean)
    val resolved = candidate.resolveTypeMetadata(httpClient) ?: return candidate
    return candidate.copy(metadata = projectTypeMetadata(resolved, credential, clean))
}

/** Shared projection for Compose, Swift and registry adapters; protocol inheritance stays in sdjwt. */
internal fun projectTypeMetadata(resolved: ResolvedTypeMetadata, credential: DigitalCredential, existing: JsonObject?): JsonObject {
    val result = existing.orEmpty().toMutableMap()
    result[TYPE_METADATA_SNAPSHOT] = resolved.snapshot()
    (resolved.effective["display"] as? JsonArray)?.let {
        result["credentialDisplay"] = JsonArray(resolved.display.map { entry ->
            val item = entry.json
            val simple = entry.rendering?.get("simple") as? JsonObject
            JsonObject((item - "rendering") + simple.orEmpty())
        })
    }
    (resolved.effective["claims"] as? JsonArray)?.let {
        // More specific selectors supply display metadata; restrictions from every selector survive.
        // Equal specificity follows document order. This is a projection policy, not inheritance.
        val claims = linkedMapOf<List<JsonElement>, JsonObject>()
        for (entry in resolved.claims.sortedByDescending { it.path.count { part -> part == MetadataClaimPathComponent.AnyIndex } }) {
            val claim = entry.json
            val display = (claim["display"] as? JsonArray)?.let { values -> JsonArray(values.map { value ->
                JsonObject(value.jsonObject.toMutableMap().apply { remove("label")?.let { put("name", it) } })
            }) }
            for (path in concretePaths(credential.credentialData, claim.getValue("path").jsonArray)) {
                val previous = claims[path]
                claims[path] = JsonObject((previous.orEmpty() + claim).toMutableMap().apply {
                    put("path", JsonArray(path))
                    display?.let { put("display", it) }
                    if (previous?.get("mandatory") == JsonPrimitive(true)) put("mandatory", JsonPrimitive(true))
                    val previousSd = previous?.get("sd")?.jsonPrimitive?.content?.takeUnless { it == "allowed" }
                    val currentSd = claim["sd"]?.jsonPrimitive?.content?.takeUnless { it == "allowed" }
                    require(previousSd == null || currentSd == null || previousSd == currentSd) {
                        "Overlapping claim selectors have incompatible disclosure restrictions"
                    }
                    (currentSd ?: previousSd)?.let { put("sd", JsonPrimitive(it)) }
                })
            }
        }
        result["credentialClaims"] = JsonArray(claims.values.toList())
    }
    return JsonObject(result)
}

private fun concretePaths(root: JsonElement, path: JsonArray): List<List<JsonElement>> {
    var matches = listOf(emptyList<JsonElement>() to root)
    for (segment in path) matches = matches.flatMap { (prefix, value) ->
        when {
            segment == JsonNull -> (value as? JsonArray)?.mapIndexed { index, element -> (prefix + JsonPrimitive(index)) to element }.orEmpty()
            segment.jsonPrimitive.isString -> (value as? JsonObject)?.get(segment.jsonPrimitive.content)
                ?.let { listOf((prefix + segment) to it) }.orEmpty()
            else -> (value as? JsonArray)?.getOrNull(segment.jsonPrimitive.int)
                ?.let { listOf((prefix + segment) to it) }.orEmpty()
        }
    }
    return matches.map { it.first }
}

/** Type identifiers from a pinned persisted snapshot, revalidated without network access. */
suspend fun StoredCredential.resolvedSdJwtVcTypes(): List<String> =
    resolveTypeMetadata(fetchIfMissing = false)?.types.orEmpty().toList()
