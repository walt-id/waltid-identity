package id.walt.x509

/**
 * DER encoded X.509 certificate as platform-agnostic wrapper.
 */
data class CertificateDer(val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        if (other !is CertificateDer) return false
        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }
}

/**
 * Validate a leaf certificate against a provided chain and trust anchors.
 *
 * @param leaf         DER-encoded end-entity certificate.
 * @param chain        DER-encoded certs (intermediates, optionally root). Order not required.
 * @param trustAnchors DER-encoded trust roots. If null/empty, an included self-signed root may be used.
 * @property enableTrustedChainRoot Flag to enable or disable the use of a trusted root certificate (self-signed) in the chain.
 * @property enableSystemTrustAnchors Flag to enable or disable the use of system trust anchors.
 * @param enableRevocation Best-effort CRL/OCSP when supported on platform.
 *
 * @throws X509ValidationException when validation fails.
 */
@Throws(X509ValidationException::class)
expect fun validateCertificateChain(
    leaf: CertificateDer,
    chain: List<CertificateDer>,
    trustAnchors: List<CertificateDer>? = null,
    enableTrustedChainRoot: Boolean = false,
    enableSystemTrustAnchors: Boolean = false,
    enableRevocation: Boolean = false
)

class X509ValidationException(message: String, cause: Throwable? = null) : Exception(message, cause)
