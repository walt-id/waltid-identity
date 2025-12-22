package id.walt.x509.iso.documentsigner.parser

import id.walt.x509.CertificateDer
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerDecodedCertificate

actual class DocumentSignerCertificateParser actual constructor(certificate: CertificateDer) {

    actual suspend fun parse(): DocumentSignerDecodedCertificate {
        TODO("Not yet implemented")
    }
}