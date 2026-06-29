package id.walt.x509.iso.iaca.builder

import id.walt.crypto.keys.Key
import id.walt.x509.X509KeyUsage
import id.walt.x509.iso.basicConstraintsExtension
import id.walt.x509.iso.buildSignumIsoCertificateDer
import id.walt.x509.iso.crlDistributionPointExtension
import id.walt.x509.iso.iaca.certificate.IACACertificateBundle
import id.walt.x509.iso.iaca.certificate.IACACertificateProfileData
import id.walt.x509.iso.iaca.parser.IACACertificateParser
import id.walt.x509.iso.generateIsoCompliantX509CertificateSerialNo
import id.walt.x509.iso.issuerAlternativeNameExtension
import id.walt.x509.iso.keyUsageExtension
import id.walt.x509.iso.subjectKeyIdentifierExtension
import id.walt.x509.iso.toSignumName
import id.walt.x509.iso.toSignumPublicKey

internal actual suspend fun platformSignIACACertificate(
    profileData: IACACertificateProfileData,
    signingKey: Key,
): IACACertificateBundle {
    val publicKey = signingKey.toSignumPublicKey()
    val serialNumber = generateIsoCompliantX509CertificateSerialNo()
    val certificateDer = buildSignumIsoCertificateDer(
        serialNumber = serialNumber,
        issuerName = profileData.principalName.toSignumName(),
        subjectName = profileData.principalName.toSignumName(),
        validityPeriod = profileData.validityPeriod,
        subjectPublicKey = signingKey,
        signingKey = signingKey,
        extensions = buildList {
            add(subjectKeyIdentifierExtension(publicKey))
            add(basicConstraintsExtension(isCa = true, pathLengthConstraint = 0))
            add(issuerAlternativeNameExtension(profileData.issuerAlternativeName))
            add(keyUsageExtension(setOf(X509KeyUsage.KeyCertSign, X509KeyUsage.CRLSign)))
            profileData.crlDistributionPointUri?.let {
                add(crlDistributionPointExtension(it))
            }
        },
    )
    return IACACertificateBundle(
        certificateDer = certificateDer,
        decodedCertificate = IACACertificateParser().parse(certificateDer),
    )
}
