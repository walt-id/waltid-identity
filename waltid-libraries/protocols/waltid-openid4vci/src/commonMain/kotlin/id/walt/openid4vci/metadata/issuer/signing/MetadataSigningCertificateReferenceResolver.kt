package id.walt.openid4vci.metadata.issuer.signing

/** Deployment-owned certificate-store lookup. Returns one PEM certificate, or null if unavailable. */
fun interface MetadataSigningCertificateReferenceResolver {
    suspend fun resolve(reference: String): String?
}
