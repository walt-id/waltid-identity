package id.waltid.openid4vci.wallet.metadata

import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata

/** Metadata resolution whose variant makes its verification state explicit. */
sealed interface ResolvedCredentialIssuerMetadata {
    val metadata: CredentialIssuerMetadata

    data class Unsigned(
        override val metadata: CredentialIssuerMetadata,
    ) : ResolvedCredentialIssuerMetadata

    data class Signed(
        override val metadata: CredentialIssuerMetadata,
        val compactJwt: String,
        val signer: MetadataSigner,
    ) : ResolvedCredentialIssuerMetadata
}

/** Stable provenance of the signer trusted by the embedding wallet. */
data class MetadataSigner(
    val keyId: String?,
    val algorithm: String,
    val trustType: MetadataSignerTrustType,
)

enum class MetadataSignerTrustType { TRUSTED_ISSUER, TRUSTED_DELEGATE }

/**
 * Verifies both the JWS signature and the signer's authority for [expectedCredentialIssuer].
 * Returning normally is the trust boundary: callers never receive decoded-but-untrusted metadata.
 */
fun interface CredentialIssuerMetadataTrustResolver {
    suspend fun verify(compactJwt: String, expectedCredentialIssuer: String): MetadataSigner
}
