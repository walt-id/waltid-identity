package id.walt.x509.iso.iaca.parser

import id.walt.x509.CertificateDer
import id.walt.x509.iso.iaca.certificate.IACADecodedCertificate

actual class IACACertificateParser actual constructor(certificate: CertificateDer) {

    actual suspend fun parse(): IACADecodedCertificate {
        TODO("Not yet implemented")
    }
}