package id.walt.x509.iso.documentsigner.parser

import id.walt.x509.CertificateDer
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerDecodedCertificate

expect class DocumentSignerCertificateParser(
    certificate: CertificateDer,
) {

    suspend fun parse(): DocumentSignerDecodedCertificate
}