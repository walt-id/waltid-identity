package id.walt.credentials.keyresolver.resolvers

import id.walt.crypto.keys.Key
import id.walt.did.dids.DidService
import io.github.oshai.kotlinlogging.KotlinLogging

object DidKeyResolver : BaseKeyResolver {
    private val log = KotlinLogging.logger { }

    suspend fun resolveKeyFromDid(issuerId: String): Key {
        log.debug { "Resolving issuer key via DID: $issuerId" }
        return DidService.resolveToKeys(issuerId).getOrThrow().firstOrNull()
            ?: throw Exception("No valid key found in DID document for $issuerId")
    }

}
