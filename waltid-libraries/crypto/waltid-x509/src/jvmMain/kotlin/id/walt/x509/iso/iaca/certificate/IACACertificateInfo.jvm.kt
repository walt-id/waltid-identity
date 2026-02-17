package id.walt.x509.iso.iaca.certificate

import id.walt.x509.X509CertificateHandle
import id.walt.x509.toJcaX509Certificate
import okio.ByteString.Companion.toByteString
import org.bouncycastle.cert.jcajce.JcaX500NameUtil

internal actual suspend fun platformExtractIacaCertificateInfoExtras(
    certificateHandle: X509CertificateHandle,
): IACACertificateInfoExtras {
    val certificate = certificateHandle.getCertificateDer().toJcaX509Certificate()
    val issuerX500Name = JcaX500NameUtil.getIssuer(certificate)
    return IACACertificateInfoExtras(
        issuingAuthority = issuerX500Name.toString(),
        issuer = issuerX500Name.encoded.toByteString(),
        subject = JcaX500NameUtil.getSubject(certificate).encoded.toByteString(),
    )
}
