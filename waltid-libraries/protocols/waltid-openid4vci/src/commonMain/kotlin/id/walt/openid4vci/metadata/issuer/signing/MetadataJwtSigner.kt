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
        ): MetadataJwtSigner? = dedicatedSigner(config, resolver, null)

        suspend fun dedicatedSigner(
            config: SignedMetadataConfig?,
            resolver: MetadataSigningKeyReferenceResolver?,
            certificateResolver: MetadataSigningCertificateReferenceResolver?,
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
                val certificateChain = signing.x5cReferences?.let { references ->
                    requireNotNull(certificateResolver) { "signedMetadata x5cReferences requires a certificate resolver" }
                    references.map { reference ->
                        val certificate = try {
                            certificateResolver.resolve(reference)
                        } catch (cause: CancellationException) {
                            throw cause
                        } catch (_: Exception) {
                            throw IllegalArgumentException("signedMetadata certificate reference could not be resolved")
                        }
                        requireNotNull(certificate) { "signedMetadata referenced certificate is unavailable" }
                    }
                } ?: material.certificateChainPem
                if (certificateChain != null) {
                    CertificateMetadataSigner.fromKey(material.key, certificateChain)
                } else {
                    JwkMetadataSigner.fromKey(material.key)
                }
            }
        }
    }
}
