package id.walt.x509.iso.iaca.certificate

import at.asitplus.signum.indispensable.pki.X509Certificate
import id.walt.x509.X509CertificateHandle
import id.walt.x509.iso.toDerByteString
import id.walt.x509.iso.toDisplayString

internal actual suspend fun platformExtractIACACertificateInfoExtras(
    certificateHandle: X509CertificateHandle,
): IACACertificateInfoExtras {
    val certificate = X509Certificate.decodeFromByteArray(
        certificateHandle.getCertificateDer().bytes.toByteArray()
    ) ?: throw IllegalArgumentException("Invalid X.509 DER certificate")

    return IACACertificateInfoExtras(
        issuingAuthority = certificate.tbsCertificate.issuerName.toDisplayString(),
        issuer = certificate.tbsCertificate.issuerName.toDerByteString(),
        subject = certificate.tbsCertificate.subjectName.toDerByteString(),
    )
}
