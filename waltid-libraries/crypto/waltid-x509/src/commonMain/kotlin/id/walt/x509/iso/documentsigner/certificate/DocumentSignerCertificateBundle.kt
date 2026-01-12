package id.walt.x509.iso.documentsigner.certificate

import id.walt.x509.CertificateDer

/**
 * Result object of building a Document Signer certificate.
 *
 * Exposes both the raw DER bytes and the decoded certificate's view so callers
 * can persist or further inspect without the need of, subsequently, parsing.
 */
data class DocumentSignerCertificateBundle(
    val certificateDer: CertificateDer,
    val decodedCertificate: DocumentSignerDecodedCertificate,
)
