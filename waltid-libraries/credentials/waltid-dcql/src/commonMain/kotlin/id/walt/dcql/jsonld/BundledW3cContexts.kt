package id.walt.dcql.jsonld

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Context documents vendored so type expansion does not fetch during presentation.
 *
 * The JSON snapshots, their source URLs, fetch dates, digests, and licences are recorded in
 * `jsonld/PROVENANCE.md`. `THIRD-PARTY-NOTICE.md` does not cover them. The Kotlin strings are
 * generated from those files by `generateBundledJsonLdContexts`.
 */
internal object BundledW3cContexts {
    const val V1_URL = "https://www.w3.org/2018/credentials/v1"
    const val V1_ALIAS_URL = "https://w3id.org/credentials/v1"
    const val V2_URL = "https://www.w3.org/ns/credentials/v2"
    const val GAIA_X_DEVELOPMENT_URL = "https://w3id.org/gaia-x/development"

    private val json = Json { ignoreUnknownKeys = true }

    private val v1 = json.parseToJsonElement(GeneratedBundledContexts.V1_JSON).jsonObject
    private val v2 = json.parseToJsonElement(GeneratedBundledContexts.V2_JSON).jsonObject
    private val gaiaXDevelopment = json.parseToJsonElement(GeneratedBundledContexts.GAIA_X_DEVELOPMENT_JSON).jsonObject

    val source: JsonLdContextDocumentSource = MapJsonLdContextDocuments(
        mapOf(
            V1_URL to v1,
            V1_ALIAS_URL to v1,
            V2_URL to v2,
            GAIA_X_DEVELOPMENT_URL to gaiaXDevelopment,
        )
    )
}
