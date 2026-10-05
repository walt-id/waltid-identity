package id.walt.openid4vci.metadata.issuer.signing

import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import kotlinx.coroutines.CancellationException

fun interface MetadataJwtSigner {
    suspend fun sign(metadata: CredentialIssuerMetadata): String

    companion object {
        /** Null retains the service's original token-key path, including its availability checks. */
        suspend fun dedicatedSigner(
            config: SignedMetadataConfig?,
            resolver: MetadataSigningKeyReferenceResolver? = null,
        ): MetadataJwtSigner? = when (val signing = config?.signingMethod) {
            null -> null
            is MetadataSigningMethod.StaticJwk -> JwkMetadataSigner.load(signing)
            is MetadataSigningMethod.X509Chain -> CertificateMetadataSigner.load(signing)
            is MetadataSigningMethod.KeyReference -> {
                requireNotNull(resolver) { "signedMetadata key-reference requires a metadata signing key resolver" }
                val material = try {
                    resolver.resolve(signing.reference)
                } catch (cause: CancellationException) {
                    throw cause
                } catch (_: Exception) {
                    throw IllegalArgumentException("signedMetadata key-reference could not be resolved")
                }
                requireNotNull(material) { "signedMetadata key-reference signing key is unavailable" }
                if (material.certificateChainPem != null) {
                    CertificateMetadataSigner.fromKey(material.key, material.certificateChainPem)
                } else {
                    JwkMetadataSigner.fromKey(material.key)
                }
            }
        }
    }
}
