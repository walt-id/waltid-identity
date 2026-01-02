package id.walt.x509.iso.iaca.certificate

import id.walt.x509.CertificateDer

/**
 * Result object of building an IACA X.509 certificate.
 *
 * Exposes both the raw DER bytes and the decoded certificate's view so callers
 * can persist or further inspect without the need of, subsequently, parsing.
 */
data class IACACertificateBundle(
    val certificateDer: CertificateDer,
    val decodedCertificate: IACADecodedCertificate,
)
