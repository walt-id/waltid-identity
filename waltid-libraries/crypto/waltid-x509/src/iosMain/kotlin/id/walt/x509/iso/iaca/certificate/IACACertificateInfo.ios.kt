package id.walt.x509.iso.iaca.certificate

import id.walt.x509.X509CertificateHandle

internal actual suspend fun platformExtractIACACertificateInfoExtras(
    certificateHandle: X509CertificateHandle,
): IACACertificateInfoExtras {
    // TODO(iOS): Implement ISO IACA certificate info extraction, then remove the guarded ISO X.509 tests.
    TODO("Not yet implemented")
}
