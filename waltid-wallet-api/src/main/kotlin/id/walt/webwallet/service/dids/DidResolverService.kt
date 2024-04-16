package id.walt.webwallet.service.dids

import kotlinx.serialization.json.JsonObject

class DidResolverService {
    suspend fun resolve(did: String): JsonObject? = id.walt.did.dids.DidService.resolve(did).getOrNull()
}