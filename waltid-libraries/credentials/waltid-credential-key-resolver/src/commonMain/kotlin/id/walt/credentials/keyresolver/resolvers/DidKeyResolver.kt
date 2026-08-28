package id.walt.credentials.keyresolver.resolvers

import id.walt.crypto.keys.Key
import id.walt.did.dids.DidService
import io.github.oshai.kotlinlogging.KotlinLogging

object DidKeyResolver : BaseKeyResolver {
    private val log = KotlinLogging.logger { }

    @Deprecated(
        "Use Crypto2JwtKeyResolver.resolveFromDid",
        ReplaceWith(
            "Crypto2JwtKeyResolver().resolveFromDid(issuerId, kid)",
            "id.walt.credentials.keyresolver.Crypto2JwtKeyResolver",
        ),
    )
    suspend fun resolveKeyFromDid(issuerId: String, kid: String? = null): Key {
        log.debug { "Resolving key via DID: $issuerId (kid=$kid)" }
        val keys = DidService.resolveToKeys(issuerId).getOrThrow()

        if (keys.isEmpty()) throw Exception("No valid key found in DID document for $issuerId")

        if (issuerId.startsWith("did:jwk:")) {
            return selectDidJwkKey(keys, issuerId, kid)
        }

        if (!kid.isNullOrBlank()) {
            val selfIdentifyingKid = kid == selfIdentifyingVerificationMethod(issuerId) ||
                kid == issuerId && issuerId.startsWith("did:key:")
            if (keys.size == 1 && selfIdentifyingKid) {
                return keys.first()
            }
            val matched = findMatchingKey(keys, kid)
            if (matched != null) {
                log.debug { "Matched key by kid '$kid' in DID document for $issuerId" }
                return matched
            }
            throw NoSuchElementException("No key with kid '$kid' found in DID document for $issuerId")
        }

        return keys.first()
    }

    /**
     * did:jwk always publishes a single verification method `{did}#0`.
     * Pre-2115 issuers wrote KMS-path or thumbprint fragments. Accept those as a
     * compatibility fallback: the DID encodes exactly one key, so kid cannot select
     * another key. Ignoring a non-`#0` kid is not spec-compliant DID URL matching;
     * new issuance must still emit `{did}#0`.
     */
    private fun selectDidJwkKey(keys: Set<Key>, did: String, kid: String?): Key {
        if (keys.size != 1) {
            throw Exception("did:jwk must resolve to exactly one verification key")
        }
        val methodKid = "$did#0"
        if (!kid.isNullOrBlank() && kid != methodKid) {
            log.warn {
                "did:jwk verification is ignoring non-spec kid '$kid' for '$did'; " +
                    "the method verification method is '$methodKid'. " +
                    "did:jwk documents contain exactly one key."
            }
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
    private suspend fun findMatchingKey(keys: Set<Key>, kid: String): Key? {
        val kidCandidates = idCandidates(kid)
        return keys.firstOrNull { key ->
            val keyId = key.getKeyId()
            idCandidates(keyId).any { it in kidCandidates }
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

    private fun selfIdentifyingVerificationMethod(did: String): String? = when {
        did.startsWith("did:key:") -> "$did#${did.removePrefix("did:key:")}"
        else -> null
    }
}
