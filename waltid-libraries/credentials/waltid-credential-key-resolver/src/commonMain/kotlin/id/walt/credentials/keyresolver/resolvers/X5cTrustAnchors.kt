package id.walt.credentials.keyresolver.resolvers

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import kotlinx.io.bytestring.ByteString

/**
 * Establishes trust in an `x5c` certificate chain against trust roots chosen by the caller.
 *
 * [X5CKeyResolver] only extracts the signing key from `x5c`; a chain carried in the token proves
 * nothing until it is validated here. Certificates inside `x5c` are never used as anchors, and the
 * platform's system trust store is never consulted.
 */
object X5cTrustAnchors {

    /**
     * Succeeds if every certificate in [x5c] is one of [trustRoots] or chains to one of them.
     *
     * @param x5c the certificate chain from the token, leaf first, as PEM or base64-encoded DER
     * @param trustRoots trusted root or pinned certificates, as PEM or base64-encoded DER; must not be empty
     */
    suspend fun validate(x5c: List<String>, trustRoots: List<String>): Result<Unit> = runCatching {
        require(x5c.isNotEmpty()) { "Certificate chain in 'x5c' must not be empty" }
        require(trustRoots.isNotEmpty()) { "No trust roots are configured for the 'x5c' certificate chain" }

        val result = X509CertificateUtil.validateCertificateChain(
            certificateChain = x5c.map(::parseCertificate),
            trustOverride = InMemoryTrustStore(trustRoots.map(::parseCertificate)),
        )
        check(result.valid) {
            "Certificate chain in 'x5c' does not chain to a configured trust root: " +
                    result.errorLog.joinToString("; ") { "${it.subjectDn}: ${it.message}" }
        }
    }

    /** Throws if any of [certificates] is not an X.509 certificate in PEM or base64-encoded DER. */
    fun requireParsable(certificates: List<String>) {
        certificates.forEach(::parseCertificate)
    }

    private fun parseCertificate(value: String): X509Certificate {
        val trimmed = value.trim()
        return if (trimmed.startsWith("-----BEGIN")) {
            X509CertificateUtil.parseCertificatePem(trimmed)
        } else {
            X509CertificateUtil.parseCertificateDerEncoded(ByteString(trimmed.decodeFromBase64()))
        }
    }
}
