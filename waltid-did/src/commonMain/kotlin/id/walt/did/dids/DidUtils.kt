package id.walt.did.dids

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@ExperimentalJsExport
@JsExport
object DidUtils {
    private const val PATTERN = "^did:([a-z]+):(.+)"
    fun methodFromDid(did: String) = did.removePrefix("did:").substringBefore(":")

    fun identifierFromDid(did: String): String? = pathFromDid(did)?.substringBefore('#')

    fun fragmentFromDid(did: String): String? = pathFromDid(did)?.substringAfter('#')

    fun pathFromDid(did: String): String? = PATTERN.toRegex().find(did)?.let {
        it.groups[2]!!.value
    }
}
