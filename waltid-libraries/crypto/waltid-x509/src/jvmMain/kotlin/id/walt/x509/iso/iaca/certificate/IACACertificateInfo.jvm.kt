package id.walt.x509.iso.iaca.certificate

import id.walt.x509.X509CertificateHandle
import id.walt.x509.toJcaX509Certificate
import kotlinx.io.bytestring.ByteString
import org.bouncycastle.cert.jcajce.JcaX500NameUtil

internal actual suspend fun platformExtractIACACertificateInfoExtras(
    certificateHandle: X509CertificateHandle,
): IACACertificateInfoExtras {
    val certificate = certificateHandle.getCertificateDer().toJcaX509Certificate()
    val issuerX500Name = JcaX500NameUtil.getIssuer(certificate)
    return IACACertificateInfoExtras(
        issuingAuthority = issuerX500Name.toString(),
        issuer = ByteString(issuerX500Name.encoded),
        subject = ByteString(JcaX500NameUtil.getSubject(certificate).encoded),
    )
}
