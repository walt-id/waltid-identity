@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.x509.CertificateDer
import id.walt.x509.X509ValidationException
import id.walt.x509.verifyOrderedCertificateChainSignatures

object X5CChainValidator {
    /**
     * Verifies the chain of DER-encoded X.509 certificates.
     * Throws [IllegalArgumentException] if any signature or AKI/SKI mismatch is found.
     * Does nothing if the chain has only one certificate.
     */
    fun verifyChain(certChainDer: List<ByteArray>) {
        try {
            verifyOrderedCertificateChainSignatures(certChainDer.map(::CertificateDer))
        } catch (e: X509ValidationException) {
            throw IllegalArgumentException(e.message, e)
        }
    }
}
