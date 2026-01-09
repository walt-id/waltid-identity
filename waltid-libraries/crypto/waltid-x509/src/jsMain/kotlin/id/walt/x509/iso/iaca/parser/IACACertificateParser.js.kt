package id.walt.x509.iso.iaca.parser

import id.walt.x509.CertificateDer
import id.walt.x509.iso.iaca.certificate.IACADecodedCertificate

internal actual suspend fun platformParseIACACertificate(
    certificate: CertificateDer,
): IACADecodedCertificate {
    TODO("Not yet implemented")
}