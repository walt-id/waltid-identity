package id.walt.x509.iso.iaca.parser

import id.walt.x509.CertificateDer
import id.walt.x509.iso.iaca.certificate.IACADecodedCertificate

class IACACertificateParser {

    suspend fun parse(certificate: CertificateDer) = platformParseIACACertificate(certificate)
}

internal expect suspend fun platformParseIACACertificate(
    certificate: CertificateDer,
): IACADecodedCertificate