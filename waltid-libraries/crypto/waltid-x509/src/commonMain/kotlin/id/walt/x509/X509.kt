package id.walt.x509

import kotlin.jvm.JvmInline

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
 * Parse Base64-encoded DER certs (e.g., from x5c header) into raw DER bytes.
 * Order doesn't matter; leaf can be first, or you pass it explicitly.
 */
expect fun parseX5cBase64(x5cBase64: List<String>): List<CertificateDer>

/**
 * Validate a leaf certificate against a provided chain and trust anchors.
 *
 * @param leaf         DER-encoded end-entity certificate.
 * @param chain        DER-encoded certs (intermediates, optionally root). Order not required.
 * @param trustAnchors DER-encoded trust roots. If null/empty, an included self-signed root may be used.
 * @param enableRevocation Best-effort CRL/OCSP when supported on platform.
 *
 * @throws X509ValidationException when validation fails.
 */
@Throws(X509ValidationException::class)
expect fun validateCertificateChain(
    leaf: CertificateDer,
    chain: List<CertificateDer>,
    trustAnchors: List<CertificateDer>? = null,
    enableRevocation: Boolean = false
)

class X509ValidationException(message: String, cause: Throwable? = null) : Exception(message, cause)
