package id.walt.x509.iso.documentsigner.parser

import id.walt.x509.CertificateDer
import id.walt.x509.iso.blockingBridge
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerDecodedCertificate

/**
 * Parser for Document Signer X.509 certificates.
 *
 * Parsing does not enforce ISO profile validations, it decodes the relevant data from the X.509 certificate.
 *
 * Use [id.walt.x509.iso.documentsigner.validate.DocumentSignerValidator] if you
 * need to enforce profile compliance and proper validation of decoded X.509 certificate's data fields.
 */
class DocumentSignerCertificateParser {

    /**
     * Parse a DER-encoded Document Signer X.509 certificate into a decoded representation.
     */
    suspend fun parse(certificate: CertificateDer) = platformParseDocumentSignerCertificate(certificate)

    /**
     * Blocking variant of [parse].
     */
    fun parseBlocking(certificate: CertificateDer): DocumentSignerDecodedCertificate = blockingBridge {
        parse(certificate)
    }
}

/**
 * Platform-specific parsing implementation for Document Signer X.509 certificates.
 */
internal expect suspend fun platformParseDocumentSignerCertificate(
    certificate: CertificateDer,
): DocumentSignerDecodedCertificate
