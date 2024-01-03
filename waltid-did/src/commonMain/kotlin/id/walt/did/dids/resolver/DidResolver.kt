package id.walt.did.dids.resolver

import id.walt.crypto.keys.Key
import kotlinx.serialization.json.JsonObject

interface DidResolver {
    val name: String

    suspend fun getSupportedMethods(): Result<Set<String>>
    suspend fun resolve(did: String): Result<JsonObject>
    suspend fun resolveToKey(did: String): Result<Key>

}
