package id.walt.x509.iso.documentsigner.parser

import id.walt.x509.CertificateDer
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerDecodedCertificate

class DocumentSignerCertificateParser {
    suspend fun parse(certificate: CertificateDer) = platformParseDocumentSignerCertificate(certificate)
}

internal expect suspend fun platformParseDocumentSignerCertificate(
    certificate: CertificateDer,
): DocumentSignerDecodedCertificate