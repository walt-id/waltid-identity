package id.walt.credentials.keyresolver.resolvers

import id.walt.crypto.keys.Key
import id.walt.did.dids.DidService
import io.github.oshai.kotlinlogging.KotlinLogging

object DidKeyResolver : BaseKeyResolver {
    private val log = KotlinLogging.logger { }

    suspend fun resolveKeyFromDid(issuerId: String, kid: String? = null): Key {
        log.debug { "Resolving key via DID: $issuerId (kid=$kid)" }
        val keys = DidService.resolveToKeys(issuerId).getOrThrow()

        if (keys.isEmpty()) throw Exception("No valid key found in DID document for $issuerId")

        if (!kid.isNullOrBlank()) {
            val matched = findMatchingKey(keys, issuerId, kid)
            if (matched != null) {
                log.debug { "Matched key by kid '$kid' in DID document for $issuerId" }
                return matched
            }
            if (keys.size == 1) {
                log.debug { "No key with kid '$kid' found in single-key DID document for $issuerId, using the only key" }
                return keys.first()
            }
            throw NoSuchElementException("No key with kid '$kid' found in DID document for $issuerId")
        }

        return keys.first()
    }

    /**
     * Attempts to find a matching key using multiple matching strategies.
     * This handles various kid formats that may be used in JWT headers:
     * - Full DID URL with fragment: did:web:example.com#key-1
     * - Just the fragment: key-1
     * - Azure Key Vault URLs: https://vault.azure.net/keys/xxx
     * - Full verification method ID: did:web:example.com#https://vault.azure.net/keys/xxx
     */
    private suspend fun findMatchingKey(keys: Set<Key>, issuerId: String, kid: String): Key? {
        val kidCandidates = idCandidates(kid)
        return keys.firstOrNull { key ->
            val keyId = key.getKeyId()
            idCandidates(keyId).any { it in kidCandidates } ||
                (keyId == issuerId && removeFragment(kid) == issuerId)
        }
    }

    private fun idCandidates(id: String): Set<String> =
        setOfNotNull(id, extractFragment(id))

    private fun extractFragment(didUrl: String): String? {
        val fragmentIndex = didUrl.indexOf('#')
        return if (fragmentIndex >= 0 && fragmentIndex < didUrl.length - 1) {
            didUrl.substring(fragmentIndex + 1)
        } else {
            null
        }
    }

    private fun removeFragment(didUrl: String): String =
        didUrl.substringBefore('#')
}
