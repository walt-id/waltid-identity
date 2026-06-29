package id.walt.x509.iso.documentsigner.builder

import id.walt.crypto.keys.Key
import id.walt.x509.X509KeyUsage
import id.walt.x509.iso.DocumentSignerEkuOID
import id.walt.x509.iso.authorityKeyIdentifierExtension
import id.walt.x509.iso.buildSignumIsoCertificateDer
import id.walt.x509.iso.crlDistributionPointExtension
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateBundle
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateProfileData
import id.walt.x509.iso.documentsigner.parser.DocumentSignerCertificateParser
import id.walt.x509.iso.extendedKeyUsageExtension
import id.walt.x509.iso.generateIsoCompliantX509CertificateSerialNo
import id.walt.x509.iso.issuerAlternativeNameExtension
import id.walt.x509.iso.keyUsageExtension
import id.walt.x509.iso.subjectKeyIdentifierExtension
import id.walt.x509.iso.toSignumName
import id.walt.x509.iso.toSignumPublicKey

internal actual suspend fun platformSignDocumentSignerCertificate(
    profileData: DocumentSignerCertificateProfileData,
    publicKey: Key,
    iacaSignerSpec: IACASignerSpecification,
): DocumentSignerCertificateBundle {
    val subjectPublicKey = publicKey.toSignumPublicKey()
    val issuerPublicKey = iacaSignerSpec.signingKey.toSignumPublicKey()
    val serialNumber = generateIsoCompliantX509CertificateSerialNo()
    val certificateDer = buildSignumIsoCertificateDer(
        serialNumber = serialNumber,
        issuerName = iacaSignerSpec.profileData.principalName.toSignumName(),
        subjectName = profileData.principalName.toSignumName(),
        validityPeriod = profileData.validityPeriod,
        subjectPublicKey = publicKey,
        signingKey = iacaSignerSpec.signingKey,
        extensions = listOf(
            authorityKeyIdentifierExtension(issuerPublicKey),
            subjectKeyIdentifierExtension(subjectPublicKey),
            keyUsageExtension(setOf(X509KeyUsage.DigitalSignature)),
            issuerAlternativeNameExtension(iacaSignerSpec.profileData.issuerAlternativeName),
            extendedKeyUsageExtension(setOf(DocumentSignerEkuOID)),
            crlDistributionPointExtension(profileData.crlDistributionPointUri),
        ),
    )
    return DocumentSignerCertificateBundle(
        certificateDer = certificateDer,
        decodedCertificate = DocumentSignerCertificateParser().parse(certificateDer),
    )
}
