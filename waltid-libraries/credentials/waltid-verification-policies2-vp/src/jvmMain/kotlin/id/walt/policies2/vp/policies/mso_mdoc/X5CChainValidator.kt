@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.x509.authorityKeyIdentifier
import id.walt.x509.subjectKeyIdentifier
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

private val log = KotlinLogging.logger {}
private val certFactory = CertificateFactory.getInstance("X.509")

actual object X5CChainValidator {

    actual fun verifyChain(certChainDer: List<ByteArray>) {
        if (certChainDer.size <= 1) return

        val certs = certChainDer.map { der ->
            certFactory.generateCertificate(ByteArrayInputStream(der)) as X509Certificate
        }

        for (i in 0 until certs.size - 1) {
            val subject = certs[i]
            val issuer = certs[i + 1]

            // Verify cryptographic signature
            try {
                subject.verify(issuer.publicKey)
            } catch (e: Exception) {
                throw IllegalArgumentException(
                    "Certificate chain validation failed at position $i: " +
                        "certificate '${subject.subjectX500Principal}' is not signed by " +
                        "'${issuer.subjectX500Principal}': ${e.message}"
                )
            }

            // Verify AKI/SKI linkage
            val subjectAki = subject.authorityKeyIdentifier?.toByteArray()
            val issuerSki = issuer.subjectKeyIdentifier?.toByteArray()
            if (subjectAki != null && issuerSki != null && !subjectAki.contentEquals(issuerSki)) {
                throw IllegalArgumentException(
                    "Certificate chain AKI/SKI mismatch at position $i: " +
                        "AKI of '${subject.subjectX500Principal}' does not match " +
                        "SKI of '${issuer.subjectX500Principal}'"
                )
            }
        }

        log.trace { "Certificate chain signature verification passed for ${certs.size} certificates" }
    }
}
