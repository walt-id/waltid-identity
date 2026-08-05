@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.x509.CertificateDer
import id.walt.mdoc.verification.validateDocumentSignerCertificateChain
import kotlin.time.Clock
import kotlin.time.Instant

object X5CChainValidator {
    /**
     * Verifies the chain of DER-encoded X.509 certificates.
     * Throws [IllegalArgumentException] if any signature or AKI/SKI mismatch is found.
     * Does nothing if the chain has only one certificate.
     */
    fun verifyChain(certChainDer: List<ByteArray>, instant: Instant = Clock.System.now()) {
        validateDocumentSignerCertificateChain(certChainDer.map(::CertificateDer), instant)
    }
}
