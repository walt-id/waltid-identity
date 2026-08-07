package id.walt.verifier2.handlers.sessioncreation

import id.walt.crypto.keys.Key
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.exportPublicJwk
import id.walt.did.dids.DidService
import id.walt.crypto2.keys.Key as Crypto2Key

private const val DECENTRALIZED_IDENTIFIER_PREFIX = "decentralized_identifier:"

/**
 * Resolves the standards-level `kid` used by a signed verifier Request Object.
 *
 * OpenID4VP `decentralized_identifier` authentication requires `kid` to identify the
 * particular DID-document verification method whose key signed the Request Object. Internal
 * KMS/Crypto2 key identifiers are therefore not suitable substitutes for DID verification-method
 * IDs. For other Client Identifier Prefixes, the existing key-ID behavior is preserved.
 */
@Suppress("DEPRECATION")
internal suspend fun requestObjectKid(clientId: String?, signingKey: Key): String {
    val did = clientId.decentralizedIdentifierOrNull() ?: return signingKey.getKeyId()
    val signingKeyThumbprint = signingKey.getPublicKey().getThumbprint()
    val matchingMethodIds = DidService.resolveToKeys(did).getOrThrow()
        .filter { resolvedKey -> resolvedKey.getPublicKey().getThumbprint() == signingKeyThumbprint }
        .map { resolvedKey -> resolvedKey.getKeyId() }
        .distinct()

    return requireSingleVerificationMethod(did, matchingMethodIds)
}

/** Crypto2 equivalent of [requestObjectKid]. */
internal suspend fun requestObjectKid(clientId: String?, signingKey: Crypto2Key): String {
    val did = clientId.decentralizedIdentifierOrNull() ?: return signingKey.id.value
    val signingKeyThumbprint = Jwk.sha256Thumbprint(signingKey.exportPublicJwk())
    val matchingMethodIds = DidService.resolveToCrypto2Keys(did).getOrThrow()
        .filter { resolvedKey -> Jwk.sha256Thumbprint(resolvedKey.exportPublicJwk()) == signingKeyThumbprint }
        .map { resolvedKey -> resolvedKey.id.value }
        .distinct()

    return requireSingleVerificationMethod(did, matchingMethodIds)
}

private fun String?.decentralizedIdentifierOrNull(): String? {
    if (this == null || !startsWith(DECENTRALIZED_IDENTIFIER_PREFIX)) return null

    val did = removePrefix(DECENTRALIZED_IDENTIFIER_PREFIX)
    require(did.isNotBlank()) { "decentralized_identifier client_id must contain a DID" }
    require('#' !in did) {
        "decentralized_identifier client_id must contain a DID, not a verification-method DID URL"
    }
    return did
}

private fun requireSingleVerificationMethod(did: String, methodIds: List<String>): String = when (methodIds.size) {
    1 -> methodIds.single()
    0 -> throw IllegalArgumentException(
        "Verifier signing key is not represented by a verificationMethod in DID '$did'"
    )
    else -> throw IllegalArgumentException(
        "Verifier signing key matches multiple verificationMethods in DID '$did': $methodIds"
    )
}
