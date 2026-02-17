package id.walt.x509.iso.iaca.certificate

import id.walt.x509.X509CertificateHandle
import id.walt.x509.toJcaX509Certificate
import okio.ByteString.Companion.toByteString
import org.bouncycastle.cert.jcajce.JcaX500NameUtil

internal actual suspend fun platformExtractIacaCertificateInfoExtras(
    certificateHandle: X509CertificateHandle,
    principalName: IACAPrincipalName,
): IACACertificateInfoExtras {
    val certificate = certificateHandle.getCertificateDer().toJcaX509Certificate()
    return IACACertificateInfoExtras(
        issuingAuthority = principalName.toJcaX500Name().toString(),
        issuer = JcaX500NameUtil.getIssuer(certificate).encoded.toByteString(),
        subject = JcaX500NameUtil.getSubject(certificate).encoded.toByteString(),
    )
}
