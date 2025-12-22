package id.walt.x509.iso.iaca.parser

import id.walt.x509.CertificateDer
import id.walt.x509.iso.iaca.certificate.IACADecodedCertificate

expect class IACACertificateParser(
    certificate: CertificateDer,
) {

    suspend fun parse(): IACADecodedCertificate
}