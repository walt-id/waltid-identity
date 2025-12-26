package id.walt.x509.iso.iaca.certificate

import id.walt.x509.CertificateDer

data class IACACertificateBundle(
    val certificateDer: CertificateDer,
    val decodedCertificate: IACADecodedCertificate,
)
