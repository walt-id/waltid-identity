package id.walt.dcql

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Caller-verified SD-JWT VC types for one matching operation. DCQL does not resolve or verify them.
 * Bindings include the credential data so an ID reused for another credential cannot inherit its types.
 * Unchanged credential copies can use the same context, independently of their implementation class.
 */
class DcqlMatchingContext(resolvedVctTypes: Map<DcqlCredential, Set<String>> = emptyMap()) {
    private data class CredentialInput(val id: String, val format: String, val data: JsonObject)

    private val types = buildMap {
        for ((credential, resolved) in resolvedVctTypes) {
            if (resolved.isEmpty()) continue
            // Own the JSON and collections even when the caller supplied mutable backing maps/sets.
            val input = CredentialInput(
                credential.id, credential.format, Json.parseToJsonElement(credential.data.toString()).jsonObject,
            )
            val previous = put(input, resolved.toSet())
            require(previous == null || previous == resolved) { "Conflicting types for the same credential input" }
        }
    }

    internal fun vctTypes(credential: DcqlCredential): Set<String> =
        if (types.isEmpty()) emptySet() else types[CredentialInput(credential.id, credential.format, credential.data)].orEmpty()

    companion object {
        val Empty = DcqlMatchingContext()
    }
}
