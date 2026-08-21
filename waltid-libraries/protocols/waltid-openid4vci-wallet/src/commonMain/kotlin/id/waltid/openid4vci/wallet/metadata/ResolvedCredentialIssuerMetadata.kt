package id.waltid.openid4vci.wallet.metadata

import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import kotlinx.serialization.Serializable

/** Metadata resolution whose variant makes its verification state explicit. */
@Serializable
sealed interface ResolvedCredentialIssuerMetadata {
    /** Parsed Credential Issuer Metadata. */
    val metadata: CredentialIssuerMetadata

    /** Metadata received as an unsigned JSON document. */
    @Serializable
    data class Unsigned(override val metadata: CredentialIssuerMetadata) : ResolvedCredentialIssuerMetadata

    /** Metadata received in a compact JWS verified by the configured trust resolver. */
    @Serializable
    data class Signed(
        override val metadata: CredentialIssuerMetadata,
        /** Exact compact JWS returned by the metadata endpoint. */
        val compactJwt: String,
        /** Trusted signer details reported by the trust resolver. */
        val signer: MetadataSigner,
    ) : ResolvedCredentialIssuerMetadata
}

/** Stable provenance of the signer trusted by the embedding wallet. */
@Serializable
data class MetadataSigner(
    /** Identifier of the trusted verification key, as reported by the trust resolver. */
    val keyId: String?,
    /** JWS algorithm verified by the trust resolver. */
    val algorithm: String,
    /** Authority relationship established by the trust resolver. */
    val trustType: MetadataSignerTrustType,
)

/** Authority relationship established for a signed metadata signer. */
@Serializable
enum class MetadataSignerTrustType {
    /** The signer is the trusted credential issuer. */
    TRUSTED_ISSUER,
    /** The signer is a trusted delegate of the credential issuer. */
    TRUSTED_DELEGATE,
}

/**
 * Verifies both the JWS signature and the signer's authority for an expected credential issuer.
 * Returning normally is the trust boundary: callers never receive decoded-but-untrusted metadata.
 */
fun interface CredentialIssuerMetadataTrustResolver {
    /**
     * @param compactJwt Compact JWS returned by the Credential Issuer Metadata endpoint.
     * @param expectedCredentialIssuer Credential issuer for which signer authority must be established.
     * @return Trusted signer details retained with the resolved metadata.
     */
    suspend fun verify(compactJwt: String, expectedCredentialIssuer: String): MetadataSigner
}
