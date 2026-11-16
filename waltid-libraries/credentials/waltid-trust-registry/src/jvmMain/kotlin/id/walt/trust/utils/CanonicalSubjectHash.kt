package id.walt.trust.utils

import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal

/**
 * The key [id.walt.trust.model.ServiceIdentity.canonicalSubjectSha256Hex] stores: SHA-256 of the RFC 2253
 * canonical name, as lowercase hex.
 *
 * PKIX chains certificates by [X500Principal.equals], which compares exactly this canonical form, so two
 * principals get the same key if and only if they are equal names.
 */
object CanonicalSubjectHash {

    fun of(principal: X500Principal): String =
        HashUtils.sha256Hex(principal.getName(X500Principal.CANONICAL).encodeToByteArray())

    /** The key of the certificate's subject, or null when [pemOrDer] is not a parseable X.509 certificate. */
    fun ofCertificate(pemOrDer: String): String? = runCatching {
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(HashUtils.decodeCertificate(pemOrDer))) as X509Certificate
        of(certificate.subjectX500Principal)
    }.getOrNull()
}
