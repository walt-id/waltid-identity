package id.walt.did.dids.resolver

import id.walt.core.crypto.keys.Key
import kotlinx.serialization.json.JsonObject

interface DidResolver {
    val name: String

    suspend fun getSupportedMethods(): Result<Set<String>>

    suspend fun resolve(did: String): Result<JsonObject>
    suspend fun resolveToKey(did: String): Result<Key>

}
