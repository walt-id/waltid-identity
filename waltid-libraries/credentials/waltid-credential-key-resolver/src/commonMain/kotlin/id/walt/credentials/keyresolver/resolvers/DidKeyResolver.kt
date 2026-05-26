package id.walt.credentials.keyresolver.resolvers

import id.walt.crypto.keys.Key
import id.walt.did.dids.DidService
import io.github.oshai.kotlinlogging.KotlinLogging

object DidKeyResolver : BaseKeyResolver {
    private val log = KotlinLogging.logger { }

    /**
     * Resolves a key from a DID document.
     * If [kid] is provided, attempts to match it against the keys in the DID document.
     * Falls back to the first key if no match is found or [kid] is null.
     */
    suspend fun resolveKeyFromDid(issuerId: String, kid: String? = null): Key {
        log.debug { "Resolving key via DID: $issuerId (kid=$kid)" }
        val keys = DidService.resolveToKeys(issuerId).getOrThrow()

        if (keys.isEmpty()) throw Exception("No valid key found in DID document for $issuerId")

        if (kid != null) {
            // Try to match by key ID — the DID key ID is typically the full DID fragment, e.g. did:example:123#key-1
            val matched = keys.firstOrNull { it.getKeyId() == kid || kid.endsWith("#${it.getKeyId()}") }
            if (matched != null) {
                log.debug { "Matched key by kid '$kid' in DID document for $issuerId" }
                return matched
            }
            log.debug { "No key with kid '$kid' found in DID document for $issuerId, falling back to first key" }
        }

        return keys.first()
    }
}
