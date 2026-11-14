package id.walt.did.dids

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
object DidUtils {

    val DEFAULT_CONTEXT =
        listOf("https://www.w3.org/ns/did/v1", "https://w3id.org/security/suites/jws-2020/v1")

    private const val PATTERN = "^did:([^:]+):(.+)"

    fun methodFromDid(did: String) = did.removePrefix("did:").substringBefore(":")

    fun identifierFromDid(did: String): String? = pathFromDid(did)?.substringBefore('#')

    fun fragmentFromDid(did: String): String? = pathFromDid(did)?.substringAfter('#')

    fun pathFromDid(did: String): String? = PATTERN.toRegex().find(did)?.let {
        it.groups[2]!!.value
    }

    fun isDidUrl(did: String): Boolean = PATTERN.toRegex().matches(did)

    /** Returns the base DID portion of a DID URL, excluding path, query, and fragment components. */
    fun didFromDidUrl(didUrl: String): String? {
        if (!isDidUrl(didUrl)) return null
        val componentIndex = didUrl.indices
            .filter { didUrl[it] == '/' || didUrl[it] == '?' || didUrl[it] == '#' }
            .minOrNull()
        val did = componentIndex?.let { didUrl.substring(0, it) } ?: didUrl
        return did.takeIf(::isDidUrl)
    }
}
