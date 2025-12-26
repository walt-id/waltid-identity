package id.walt.x509.iso.documentsigner.certificate

import id.walt.x509.CertificateDer

data class DocumentSignerCertificateBundle(
    val certificateDer: CertificateDer,
    val decodedCertificate: DocumentSignerDecodedCertificate,
)
