package id.walt.x509.iso.documentsigner.parser

import id.walt.x509.CertificateDer
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerDecodedCertificate

class DocumentSignerCertificateParser(
    val certificate: CertificateDer,
) {
    suspend fun parse() = platformParseDocumentSignerCertificate(certificate)
}

internal expect suspend fun platformParseDocumentSignerCertificate(
    certificate: CertificateDer,
): DocumentSignerDecodedCertificate