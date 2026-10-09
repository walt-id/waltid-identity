package id.walt.openid4vci.metadata.issuer.signing

import id.walt.crypto2.keys.Key

/** Deployment-owned lookup of signing material; references are not public verification-key lookups. */
fun interface MetadataSigningKeyReferenceResolver {
    suspend fun resolve(reference: String): ResolvedMetadataSigningKey?
}

/** Optional signer certificate chain, leaf first. Null publishes a public JWK; an empty chain is invalid. */
class ResolvedMetadataSigningKey(
    val key: Key,
    val certificateChainPem: List<String>? = null,
) {
    override fun toString(): String = "ResolvedMetadataSigningKey([redacted])"
}
