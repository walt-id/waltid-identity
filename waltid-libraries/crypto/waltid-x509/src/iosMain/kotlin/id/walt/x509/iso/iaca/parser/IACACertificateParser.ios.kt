package id.walt.x509.iso.iaca.parser

import id.walt.x509.CertificateDer
import id.walt.x509.iso.iaca.certificate.IACADecodedCertificate

internal actual suspend fun platformParseIACACertificate(
    certificate: CertificateDer,
): IACADecodedCertificate {
    // TODO(iOS): Implement ISO IACA certificate parsing, then remove the guarded ISO X.509 tests.
    TODO("Not yet implemented")
}
